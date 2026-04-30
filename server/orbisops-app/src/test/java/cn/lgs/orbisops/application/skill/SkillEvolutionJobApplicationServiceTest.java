package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionMessage;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionTraceEvent;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionInputPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillEvolutionJobApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T05:00:00Z");

    @Test
    void enqueueOwnsValidationAvailabilityIdentityPersistenceAndAudit() {
        Fixture fixture = fixture();
        when(fixture.repository.available()).thenReturn(false, true);
        when(fixture.repository.enqueue(any(SkillEvolutionJobSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SkillEvolutionEnqueueResult missing = fixture.service.enqueue(" ", "", "", "", "");
        SkillEvolutionEnqueueResult unavailable = fixture.service.enqueue("run-1", "", "", "", "");
        SkillEvolutionEnqueueResult queued = fixture.service.enqueue(
                " run-1 ", " session-1 ", " demo-project ", " agent-1 ", " ");

        assertAll(
                () -> assertFalse(missing.queued()),
                () -> assertEquals("SKIP_MISSING_CANONICAL_RUN_ID", missing.reason()),
                () -> assertFalse(unavailable.queued()),
                () -> assertEquals("DB_UNAVAILABLE", unavailable.reason()),
                () -> assertTrue(queued.queued()),
                () -> assertTrue(queued.job().jobId().startsWith("skill-evo-")),
                () -> assertEquals("RUN_COMPLETED", queued.job().triggerReason()),
                () -> assertEquals(NOW, queued.job().nextRunAt()));
        verify(fixture.audit).recordJobCreated(queued.job());
    }

    @Test
    void batchProcessesCandidateAndCompletesJob() {
        Fixture fixture = fixture();
        SkillEvolutionJobSnapshot job = job(0);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace("run-1")).thenReturn(qualifyingTrace());
        when(fixture.chat.messages("session-1", 200)).thenReturn(qualifyingChat());
        when(fixture.pipeline.decide(any(SkillEvolutionPipelineRequest.class))).thenReturn(
                new SkillEvolutionPipelineDecision(
                        "CANDIDATE", "ENOUGH_SIGNAL", "skill-1", "", "{\"candidateId\":\"candidate-1\"}", "CANDIDATE"));
        fixture.patchId.set("skill-patch-1");
        when(fixture.repository.complete(any(),any(SkillEvolutionPatchSnapshot.class),any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        List<SkillEvolutionJobRunResult> results = fixture.service.runBatch(1, 3);

        SkillEvolutionPatchSnapshot patch = results.get(0).patch();
        Map<String, Object> patchPayload = new SkillEvolutionPayloadCodec().decodeObject(patch.patchJson());
        assertAll(
                () -> assertEquals(1, results.size()),
                () -> assertEquals("skill-patch-1", patch.patchId()),
                () -> assertEquals("UPDATE_SKILL_CANDIDATE", patch.decision()),
                () -> assertEquals("skill-1", patch.targetSkillId()),
                () -> assertEquals("CANDIDATE", patch.status()),
                () -> assertEquals("{\"candidateId\":\"candidate-1\"}", patchPayload.get("content")),
                () -> assertTrue(patchPayload.get("summary") instanceof Map<?, ?>),
                () -> assertEquals("{\"candidateId\":\"candidate-1\"}", patch.validationJson()));
        verify(fixture.repository).complete(eq(job),any(SkillEvolutionPatchSnapshot.class),eq(SkillEvolutionJobStatus.COMPLETED));
        verify(fixture.audit).recordJobCompleted(job, "UPDATE_SKILL_CANDIDATE", patch, SkillEvolutionJobStatus.COMPLETED);
    }

    @Test
    void batchPreservesSkipDecisionMatchedSkillAndSkippedTerminalState() {
        Fixture fixture = fixture();
        SkillEvolutionJobSnapshot job = job(0);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace(anyString())).thenReturn(qualifyingTrace());
        when(fixture.chat.messages(anyString(), eq(200))).thenReturn(qualifyingChat());
        when(fixture.pipeline.decide(any(SkillEvolutionPipelineRequest.class))).thenReturn(
                new SkillEvolutionPipelineDecision(
                        "SKIPPED", "SKIP_LOW_SIGNAL", "", "skill-existing", "{}", "SKIPPED"));
        fixture.patchId.set("skill-patch-skip");
        when(fixture.repository.complete(any(),any(SkillEvolutionPatchSnapshot.class),any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        SkillEvolutionPatchSnapshot patch = fixture.service.runBatch(1, 3).get(0).patch();
        Map<String, Object> patchPayload = new SkillEvolutionPayloadCodec().decodeObject(patch.patchJson());
        Map<String, Object> validation = new SkillEvolutionPayloadCodec().decodeObject(patch.validationJson());

        assertAll(
                () -> assertEquals("SKIP_LOW_SIGNAL", patch.decision()),
                () -> assertEquals("SKIP_LOW_SIGNAL", patch.skippedReason()),
                () -> assertEquals("skill-existing", patch.targetSkillId()),
                () -> assertEquals("SKIPPED", patch.status()),
                () -> assertEquals("", patchPayload.get("content")),
                () -> assertEquals(true, validation.get("valid")),
                () -> assertEquals("SKIP_LOW_SIGNAL", validation.get("reason")));
        verify(fixture.repository).complete(eq(job),any(SkillEvolutionPatchSnapshot.class),eq(SkillEvolutionJobStatus.SKIPPED));
    }

    @Test
    void pipelineFailureReschedulesClaimAndAuditsTransition() {
        Fixture fixture = fixture();
        SkillEvolutionJobSnapshot job = job(0);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace(anyString())).thenReturn(qualifyingTrace());
        when(fixture.chat.messages(anyString(), eq(200))).thenReturn(qualifyingChat());
        when(fixture.pipeline.decide(any(SkillEvolutionPipelineRequest.class)))
                .thenThrow(new IllegalStateException("pipeline unavailable"));

        when(fixture.repository.rescheduleOrFail(any(),any(),any())).thenReturn(true);
        SkillEvolutionJobRunResult result = fixture.service.runBatch(1, 3).get(0);

        ArgumentCaptor<SkillEvolutionRetryTransition> transition = ArgumentCaptor.forClass(SkillEvolutionRetryTransition.class);
        verify(fixture.repository).rescheduleOrFail(eq(job), transition.capture(), eq("pipeline unavailable"));
        assertAll(
                () -> assertEquals("PENDING", result.status()),
                () -> assertEquals("pipeline unavailable", result.error()),
                () -> assertEquals(1, transition.getValue().attempts()),
                () -> assertEquals(NOW.plusSeconds(60), transition.getValue().nextRunAt()),
                () -> assertEquals(SkillEvolutionJobStatus.PENDING, transition.getValue().status()));
        verify(fixture.audit).recordJobFailed(job, transition.getValue(), "pipeline unavailable");
        verify(fixture.repository, never()).complete(any(),any(),any());
    }

    @Test
    void missingModelWaitsWithoutBurningTheRemainingAttemptOrCompletingAPatch() {
        Fixture fixture=fixture(); var job=job(2);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace(anyString())).thenReturn(qualifyingTrace());
        when(fixture.chat.messages(anyString(),eq(200))).thenReturn(qualifyingChat());
        when(fixture.pipeline.decide(any())).thenThrow(new IllegalStateException("SKILL_EVOLUTION_MODEL_UNAVAILABLE"));
        when(fixture.repository.rescheduleOrFail(any(),any(),any())).thenReturn(true);
        assertEquals("PENDING",fixture.service.runBatch(1,3).get(0).status());
        var transition=ArgumentCaptor.forClass(SkillEvolutionRetryTransition.class);
        verify(fixture.repository).rescheduleOrFail(eq(job),transition.capture(),eq("SKILL_EVOLUTION_MODEL_UNAVAILABLE"));
        assertEquals(2,transition.getValue().attempts());
        assertEquals(NOW.plusSeconds(60),transition.getValue().nextRunAt());
        verify(fixture.repository,never()).complete(any(),any(),any());
    }

    @Test void pendingAssetProposalPreservesEvidenceAndAttemptBudget() {
        Fixture fixture=fixture();var job=job(2);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace(anyString())).thenReturn(qualifyingTrace());
        when(fixture.chat.messages(anyString(),eq(200))).thenReturn(qualifyingChat());
        when(fixture.pipeline.decide(any())).thenThrow(new IllegalStateException("SKILL_EVOLUTION_PROPOSAL_PENDING"));
        when(fixture.repository.rescheduleOrFail(any(),any(),any())).thenReturn(true);
        assertEquals("PENDING",fixture.service.runBatch(1,3).get(0).status());
        var transition=ArgumentCaptor.forClass(SkillEvolutionRetryTransition.class);
        verify(fixture.repository).rescheduleOrFail(eq(job),transition.capture(),eq("SKILL_EVOLUTION_PROPOSAL_PENDING"));
        assertEquals(2,transition.getValue().attempts());assertEquals(NOW.plusSeconds(900),transition.getValue().nextRunAt());
        verify(fixture.repository,never()).complete(any(),any(),any());
    }

    @Test
    void backgroundObserverEnqueuesOnlyRepositorySelectedCompletedRuns() {
        Fixture fixture = fixture();
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.findUnqueuedRunCandidates(50)).thenReturn(List.of(
                new SkillEvolutionRunCandidate(
                        "run-background", "session-bg", "demo-project", "agent-bg",
                        "SUCCEEDED", NOW)));
        when(fixture.repository.enqueue(any(SkillEvolutionJobSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SkillEvolutionEnqueueResult> results =
                fixture.service.enqueueUnobservedCompletedRuns(50);

        assertAll(
                () -> assertEquals(1, results.size()),
                () -> assertTrue(results.get(0).queued()),
                () -> assertEquals("run-background", results.get(0).job().runId()),
                () -> assertEquals("RUN_COMPLETED_BACKGROUND", results.get(0).job().triggerReason()));
        verify(fixture.repository).findUnqueuedRunCandidates(50);
        verify(fixture.audit).recordJobCreated(results.get(0).job());
    }

    @Test
    void cheapGateSkipsLowSignalRunBeforeCallingPipeline() {
        Fixture fixture = fixture();
        SkillEvolutionJobSnapshot job = job(0);
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.claimPending(3)).thenReturn(Optional.of(job));
        when(fixture.trace.trace(anyString())).thenReturn(List.of(
                new SkillEvolutionTraceEvent("RUN_FINISHED", "SUCCEEDED", "done", Map.of())));
        when(fixture.chat.messages(anyString(), eq(200))).thenReturn(qualifyingChat());
        when(fixture.repository.complete(any(),any(SkillEvolutionPatchSnapshot.class),any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        SkillEvolutionPatchSnapshot patch = fixture.service.runBatch(1, 3).get(0).patch();

        assertAll(
                () -> assertEquals("SKIP_NO_TOOL_EVIDENCE", patch.decision()),
                () -> assertEquals("SKIP_NO_TOOL_EVIDENCE", patch.skippedReason()),
                () -> assertEquals("SKIPPED", patch.status()));
        verify(fixture.pipeline, never()).decide(any());
        verify(fixture.repository).complete(eq(job),any(SkillEvolutionPatchSnapshot.class),eq(SkillEvolutionJobStatus.SKIPPED));
    }

    @Test
    void typedQueriesOwnStatusValidationAndLimits() {
        Fixture fixture = fixture();
        when(fixture.repository.available()).thenReturn(true);
        when(fixture.repository.findJobs(SkillEvolutionJobStatus.PENDING, 500)).thenReturn(List.of(job(0)));
        when(fixture.repository.findJob("job-1")).thenReturn(Optional.of(job(0)));
        when(fixture.repository.findPatches("job-1", "", 1)).thenReturn(List.of(patch()));

        assertAll(
                () -> assertEquals(1, fixture.service.listJobs("pending", 999).size()),
                () -> assertEquals(List.of(), fixture.service.listJobs("unknown", 10)),
                () -> assertEquals("job-1", fixture.service.getJob("job-1").jobId()),
                () -> assertEquals(1, fixture.service.listPatches("job-1", "", 0).size()),
                () -> assertNotNull(fixture.service.getJob("job-1")));
    }

    private Fixture fixture() {
        ISkillEvolutionJobRepository repository = mock(ISkillEvolutionJobRepository.class);
        SkillEvolutionTraceInputPort trace = mock(SkillEvolutionTraceInputPort.class);
        SkillEvolutionChatInputPort chat = mock(SkillEvolutionChatInputPort.class);
        SkillEvolutionPipelinePort pipeline = mock(SkillEvolutionPipelinePort.class);
        SkillEvolutionPatchJsonEncoder json = new SkillEvolutionPatchJsonEncoder();
        AtomicReference<String> patchId = new AtomicReference<>("skill-patch-default");
        SkillEvolutionAuditPort audit = mock(SkillEvolutionAuditPort.class);
        SkillEvolutionJobApplicationService service = new SkillEvolutionJobApplicationService(
                repository,
                new SkillEvolutionJobPolicy(),
                new SkillEvolutionInputPolicy(),
                trace,
                chat,
                pipeline,
                json,
                patchId::get,
                audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(repository, trace, chat, pipeline, json, patchId, audit, service);
    }

    private List<SkillEvolutionTraceEvent> qualifyingTrace() {
        return List.of(
                new SkillEvolutionTraceEvent("RUN_FINISHED", "SUCCEEDED", "done", Map.of()),
                new SkillEvolutionTraceEvent(
                        "TOOL_CALL_FINISHED",
                        "SUCCEEDED",
                        "tool evidence",
                        Map.of(
                                "evidenceId", "evidence-1",
                                "resultId", "result-1",
                                "outputHash", "hash-1")));
    }

    private List<SkillEvolutionMessage> qualifyingChat() {
        return List.of(new SkillEvolutionMessage("user", "goal"));
    }

    private SkillEvolutionJobSnapshot job(int attempts) {
        return new SkillEvolutionJobSnapshot(
                1L, "job-1", "run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED",
                SkillEvolutionJobStatus.PENDING, attempts, NOW, "", null, null);
    }

    private SkillEvolutionPatchSnapshot patch() {
        return new SkillEvolutionPatchSnapshot(
                1L, "patch-1", "job-1", "run-1", "demo-project", "skill-1",
                "UPDATE_SKILL_CANDIDATE", "{}", "{}", "CANDIDATE", null, "", null, null);
    }

    private record Fixture(
            ISkillEvolutionJobRepository repository,
            SkillEvolutionTraceInputPort trace,
            SkillEvolutionChatInputPort chat,
            SkillEvolutionPipelinePort pipeline,
            SkillEvolutionPatchJsonEncoder json,
            AtomicReference<String> patchId,
            SkillEvolutionAuditPort audit,
            SkillEvolutionJobApplicationService service) {
    }
}
