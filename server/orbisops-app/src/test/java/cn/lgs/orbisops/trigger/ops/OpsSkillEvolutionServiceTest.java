package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.skill.SkillEvolutionEnqueueResult;
import cn.lgs.orbisops.application.skill.SkillEvolutionJobApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionJobRunResult;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillEvolutionJobMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillEvolutionServiceTest {

    @Test
    void triggerAndWorkerFlagsRemainInLegacyFacade() {
        SkillEvolutionJobApplicationService application = mock(SkillEvolutionJobApplicationService.class);
        OpsSkillEvolutionService service = new OpsSkillEvolutionService(
                application,
                new OpsSkillEvolutionJobMapper());

        assertAll(
                () -> assertEquals(Map.of("queued", false, "reason", "TRIGGER_DISABLED"),
                        service.enqueue("run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED")),
                () -> assertEquals(List.of(Map.of("status", "SKIPPED", "reason", "WORKER_DISABLED")),
                        service.runBatch()));
        verify(application, never()).enqueue("run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED");
        verify(application, never()).runBatch(0, 0);
    }

    @Test
    void delegatesEnabledCommandsAndQueriesToTypedApplicationService() {
        SkillEvolutionJobApplicationService application = mock(SkillEvolutionJobApplicationService.class);
        OpsSkillEvolutionService service = new OpsSkillEvolutionService(
                application,
                new OpsSkillEvolutionJobMapper(),
                new OpsSkillEvolutionSettings(true, true, 2, 3, 60_000L));
        SkillEvolutionJobSnapshot job = job();
        SkillEvolutionPatchSnapshot patch = patch();
        when(application.enqueue("run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED"))
                .thenReturn(SkillEvolutionEnqueueResult.queued(job));
        when(application.runBatch(2, 3)).thenReturn(List.of(SkillEvolutionJobRunResult.completed(patch)));
        when(application.listJobs("PENDING", 20)).thenReturn(List.of(job));
        when(application.getJob("job-1")).thenReturn(job);
        when(application.listPatches("job-1", "UPDATE_SKILL_CANDIDATE", 20)).thenReturn(List.of(patch));
        Map<String, Object> queued = service.enqueue(
                "run-1", "session-1", "demo-project", "agent-1", "RUN_COMPLETED");
        Map<String, Object> result = service.runBatch().get(0);

        assertAll(
                () -> assertEquals("job-1", queued.get("jobId")),
                () -> assertEquals("RUN_COMPLETED", queued.get("triggerReason")),
                () -> assertEquals("patch-1", result.get("patchId")),
                () -> assertEquals("job-1", service.listJobs("PENDING", 20).get(0).get("jobId")),
                () -> assertEquals("job-1", service.getJob("job-1").get("jobId")),
                () -> assertEquals("patch-1", service.listPatches(
                        "job-1", "UPDATE_SKILL_CANDIDATE", 20).get(0).get("patchId")));
        verify(application).runBatch(2, 3);
    }

    @Test
    void attachesCurrentPublicationInOneBatchWhileKeepingTheGeneratedStatus() {
        var application=mock(SkillEvolutionJobApplicationService.class);
        var diagnostics=mock(cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.class);
        var patch=new SkillEvolutionPatchSnapshot(1,"patch","job","run","project","old-target",
                "CREATE_SKILL_CANDIDATE","{\"content\":\"{\\\"candidateId\\\":\\\"candidate\\\"}\"}","{}","PENDING_INDEX",null,"",null,null);
        when(application.listPatches("job","",20)).thenReturn(List.of(patch));
        var ref=new cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.PublicationRef("project","candidate");
        var publication=new cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.PublicationState("ACTIVE","MERGE_SKILLS",List.of("merged"),1);
        when(diagnostics.publications(List.of(ref))).thenReturn(Map.of(ref,publication));
        var service=new OpsSkillEvolutionService(application,new OpsSkillEvolutionJobMapper(),null,diagnostics);
        var row=service.listPatches("job","",20).get(0);
        assertEquals("PENDING_INDEX",row.get("status"));assertEquals(publication,row.get("publication"));
        verify(diagnostics).publications(List.of(ref));
    }

    @Test void jobDetailsProjectAuthoredReleaseWithoutReplacingTheFailedJobOutcome() {
        var application=mock(SkillEvolutionJobApplicationService.class);
        var diagnostics=mock(cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.class);
        var job=new SkillEvolutionJobSnapshot(1,"job","run","session","project","agent","RUN_COMPLETED",
                SkillEvolutionJobStatus.FAILED,8,null,"SYNTHETIC_FAILURE",null,null,"source","",1,0);
        when(application.getJob("job")).thenReturn(job);
        var publication=Map.<String,Object>of("candidateId","candidate","status","ROLLED_BACK","releasedVersion",0,"reason","BASELINE_STALE");
        when(diagnostics.authoredPublication("project","job","source")).thenReturn(publication);
        var service=new OpsSkillEvolutionService(application,new OpsSkillEvolutionJobMapper(),null,diagnostics);
        var detail=service.getJob("job");
        assertEquals("FAILED",detail.get("status"));assertEquals(8,detail.get("attempts"));
        assertEquals(publication,detail.get("authoredPublication"));
        verify(diagnostics).authoredPublication("project","job","source");
    }

    @Test void noChangeConclusionIsProjectedWithoutRewritingTheHistoricalJobOrClaimingPublication() {
        var application=mock(SkillEvolutionJobApplicationService.class);
        var diagnostics=mock(cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.class);
        var job=new SkillEvolutionJobSnapshot(1,"job","run","session","project","agent","TASK_ACCEPTED",
                SkillEvolutionJobStatus.SKIPPED,2,null,"",null,null,"source","",2,0,0);
        when(application.getJob("job")).thenReturn(job);
        var decision=new cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort.AuthoredDecision(
                "plan","NO_CHANGE","SYNTHETIC already covered","SYNTHETIC_TEST","",true);
        when(diagnostics.authoredDecision("project","job","source")).thenReturn(java.util.Optional.of(decision));
        var detail=new OpsSkillEvolutionService(application,new OpsSkillEvolutionJobMapper(),null,diagnostics).getJob("job");
        assertEquals("SKIPPED",detail.get("status"));assertEquals(2,detail.get("attempts"));
        assertEquals(decision,detail.get("authoredDecision"));
        org.junit.jupiter.api.Assertions.assertFalse(detail.containsKey("authoredPublication"));
    }

    private SkillEvolutionJobSnapshot job() {
        return new SkillEvolutionJobSnapshot(
                1L,
                "job-1",
                "run-1",
                "session-1",
                "demo-project",
                "agent-1",
                "RUN_COMPLETED",
                SkillEvolutionJobStatus.PENDING,
                0,
                Instant.parse("2026-07-22T05:00:00Z"),
                "",
                null,
                null);
    }

    private SkillEvolutionPatchSnapshot patch() {
        return new SkillEvolutionPatchSnapshot(
                1L,
                "patch-1",
                "job-1",
                "run-1",
                "demo-project",
                "skill-1",
                "UPDATE_SKILL_CANDIDATE",
                "{}",
                "{}",
                "CANDIDATE",
                null,
                "",
                null,
                null);
    }
}
