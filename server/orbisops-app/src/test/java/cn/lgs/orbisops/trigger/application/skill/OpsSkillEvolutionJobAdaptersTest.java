package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillEvolutionPatchJsonEncoder;
import cn.lgs.orbisops.application.skill.SkillEvolutionSimilarityMatch;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatMessageView;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChatSessionService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringAgent;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillSimilarityService;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillEvolutionJobAdaptersTest {

    @Test
    void inputAdapterMapsRuntimeViewsToNeutralModels() {
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        OpsChatSessionService chats = mock(OpsChatSessionService.class);
        when(graphEvents.list("run-1")).thenReturn(List.of(new GraphEvent(
                "run-1", "", 1L, "TOOL_FINISHED", "node-1", "TOOL", "agent-1", "runtime",
                "SUCCEEDED", "done", "", "", 5L, Map.of(
                        "contextBundleHash", "ctx-1",
                        "content", "tool result"))));
        when(chats.messages("session-1", 200)).thenReturn(List.of(
                OpsChatMessageView.builder().role("user").content("goal").build()));
        OpsSkillEvolutionInputAdapter adapter = new OpsSkillEvolutionInputAdapter(graphEvents, chats);

        assertAll(
                () -> assertEquals("TOOL_FINISHED", adapter.trace("run-1").get(0).eventType()),
                () -> assertEquals("tool result", adapter.trace("run-1").get(0).content()),
                () -> assertEquals("ctx-1", adapter.trace("run-1").get(0).payload().get("contextBundleHash")),
                () -> assertEquals("user", adapter.messages("session-1", 200).get(0).role()),
                () -> assertEquals("goal", adapter.messages("session-1", 200).get(0).content()),
                () -> assertEquals(List.of(), adapter.trace(" ")),
                () -> assertEquals(List.of(), adapter.messages("", 200)));
    }

    @Test
    void pipelineAdapterOwnsAuthoringSimilarityJsonAndAuditBoundaries() {
        OpsSkillAuthoringAgent authoring = mock(OpsSkillAuthoringAgent.class);
        OpsSkillSimilarityService similarity = mock(OpsSkillSimilarityService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        when(authoring.author(anyMap())).thenReturn(Map.of(
                "patchType", "UPDATE_DIAGNOSTIC_RECIPE"));
        when(similarity.bestMatch(eq("demo-project"), anyMap())).thenReturn(Map.of(
                "skillId", "skill-1",
                "similarity", 0.91D));
        OpsSkillEvolutionPipelineAdapter adapter =
                new OpsSkillEvolutionPipelineAdapter(authoring, similarity, audit);

        SkillEvolutionAuthoredCandidate authored = adapter.author(Map.of("goal", "diagnose"));
        SkillEvolutionSimilarityMatch matched = adapter.bestMatch("demo-project", authored.payload());
        adapter.recordSkipped(
                "demo-project",
                "agent-1",
                "run-1",
                "SKIP_NO_SIGNAL",
                Map.of());
        adapter.recordCandidateCreated(
                "demo-project",
                "agent-1",
                "candidate-1",
                "CANARY",
                Map.of("releaseId", "release-1"));

        assertAll(
                () -> assertEquals(
                        "UPDATE_DIAGNOSTIC_RECIPE",
                        authored.patchType()),
                () -> assertEquals("skill-1", matched.skillId()));
        verify(audit).recordRuntimeEvent(
                eq("demo-project"),
                eq("agent-1"),
                eq(""),
                eq("skill-evolution"),
                eq("SKILL_EVOLUTION_SKIPPED"),
                eq("run-1"),
                eq("LOW"),
                eq("SKIPPED"),
                any());
        verify(audit).recordRuntimeEvent(
                eq("demo-project"),
                eq("agent-1"),
                eq(""),
                eq("skill-evolution"),
                eq("SKILL_EVOLUTION_CANDIDATE_CREATED"),
                eq("candidate-1"),
                eq("LOW"),
                eq("CANARY"),
                any());
    }

    @Test
    void jsonIdentityClockAndAuditAdaptersOwnOuterLayerConcerns() {
        SkillEvolutionPatchJsonEncoder json = new SkillEvolutionPatchJsonEncoder();
        String patchJson = json.encodePatch("{\"candidateId\":\"candidate-1\"}", summary());
        String skipValidation = json.encodeSkipValidation("SKIP_UNSAFE_OUTPUT", "unsafe");
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsSkillEvolutionAuditAdapter audit = new OpsSkillEvolutionAuditAdapter(auditService);
        SkillEvolutionJobSnapshot job = job();
        SkillEvolutionPatchSnapshot patch = patch();

        audit.recordJobCreated(job);
        audit.recordJobCompleted(job, patch.decision(), patch, SkillEvolutionJobStatus.COMPLETED);
        audit.recordJobFailed(job, new SkillEvolutionRetryTransition(
                SkillEvolutionJobStatus.PENDING, 1, Instant.parse("2026-07-22T05:01:00Z")), "temporary");

        assertAll(
                () -> assertEquals("goal", JSON.parseObject(patchJson).getJSONObject("summary").getString("normalizedUserGoal")),
                () -> assertFalse(JSON.parseObject(skipValidation).getBooleanValue("valid")));
        verify(auditService).recordRuntimeEvent(
                eq("demo-project"), eq("agent-1"), eq(""), eq("skill-evolver"), eq("job-create"),
                eq("job-1"), eq("LOW"), eq("PENDING"), any());
        verify(auditService).recordRuntimeEvent(
                eq("demo-project"), eq("agent-1"), eq(""), eq("skill-evolver"), eq("job-run"),
                eq("job-1"), eq("LOW"), eq("COMPLETED"), any());
        verify(auditService).recordRuntimeEvent(
                eq("demo-project"), eq("agent-1"), eq(""), eq("skill-evolver"), eq("job-fail"),
                eq("job-1"), eq("LOW"), eq("PENDING"), any());
    }

    private SkillEvolutionInputSummary summary() {
        return new SkillEvolutionInputSummary(
                List.of("RUN_FINISHED:SUCCEEDED:done"),
                List.of("tool"),
                "goal",
                "done",
                true,
                true,
                true,
                List.of(new SkillEvolutionEvidenceReference("evidence-1", "result-1", "hash-1")),
                "ctx-1");
    }

    private SkillEvolutionJobSnapshot job() {
        return new SkillEvolutionJobSnapshot(
                1L, "job-1", "run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED",
                SkillEvolutionJobStatus.PENDING, 0, Instant.parse("2026-07-22T05:00:00Z"), "", null, null);
    }

    private SkillEvolutionPatchSnapshot patch() {
        return new SkillEvolutionPatchSnapshot(
                1L, "patch-1", "job-1", "run-1", "demo-project", "skill-1",
                "UPDATE_SKILL_CANDIDATE", "{}", "{}", "CANDIDATE", null, "", null, null);
    }
}
