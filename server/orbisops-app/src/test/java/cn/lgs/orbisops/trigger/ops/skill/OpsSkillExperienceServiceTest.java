package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillExperienceApplicationService;
import cn.lgs.orbisops.application.skill.SkillExperienceAuditPort;
import cn.lgs.orbisops.application.skill.SkillExperiencePort;
import cn.lgs.orbisops.application.skill.SkillTransactionPort;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.domain.skill.service.SkillExperienceObservationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillExperienceServiceTest {

    @Test
    void recordsSanitizedEpisodeObservationAndCluster() {
        SkillExperiencePort port = mock(SkillExperiencePort.class);
        SkillExperienceAuditPort audit = mock(SkillExperienceAuditPort.class);
        when(port.insertObservation(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(port.cluster(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SkillExperienceClusterSnapshot(
                        1,
                        1,
                        1,
                        "ACCUMULATING",
                        1));
        OpsSkillExperienceService service = service(port, audit);

        Map<String, Object> result = service.recordObservation(Map.of(
                "projectId", "demo-project",
                "agentId", "ops",
                "runId", "run-123456789",
                "sessionId", "session-1",
                "normalizedUserGoal", "查询 orderId=987654321 的失败原因 password=plain-secret",
                "finalOutput", "通过 Prometheus 和日志交叉验证得到根因，password=plain-secret。".repeat(4),
                "completed", true,
                "toolEvidence", List.of(
                        "Prometheus metric result",
                        "Elasticsearch log result"),
                "eventSummaries", List.of("TOOL_FINISHED", "FINAL_OUTPUT"),
                "evidenceRefs", List.of(Map.of(
                        "evidenceId", "e-1",
                        "resultId", "result-1",
                        "outputHash", "hash-1",
                        "sourceType", "TOOL"))),
                "SUCCESSFUL_DIAGNOSTIC_PATTERN");

        @SuppressWarnings("unchecked")
        Map<String, Object> task =
                (Map<String, Object>) result.get("taskTemplate");
        String problemPattern = String.valueOf(task.get("problemPattern"));
        assertFalse(problemPattern.contains("987654321"));
        assertFalse(problemPattern.contains("plain-secret"));
        assertTrue(problemPattern.contains("{id}"));
        assertEquals(List.of(
                        "QUERY_METRICS",
                        "QUERY_LOGS",
                        "SYNTHESIZE_VERIFIED_OUTCOME"),
                result.get("abstractTrajectory"));
        assertEquals(1, result.get("observationCount"));
        ArgumentCaptor<SkillExperienceObservation> observation =
                ArgumentCaptor.forClass(SkillExperienceObservation.class);
        verify(port).upsertEpisode(observation.capture());
        verify(port).insertObservation(observation.getValue());
        verify(port).upsertCluster(observation.getValue());
        verify(audit).recordObservation(observation.getValue());
        assertFalse(observation.getValue().finalSummary().contains("plain-secret"));
    }

    @Test
    void retryingSameObservationDoesNotIncrementClusterAgain() {
        SkillExperiencePort port = mock(SkillExperiencePort.class);
        SkillExperienceAuditPort audit = mock(SkillExperienceAuditPort.class);
        when(port.insertObservation(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(port.cluster(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SkillExperienceClusterSnapshot(
                        1,
                        1,
                        1,
                        "ACCUMULATING",
                        1));
        OpsSkillExperienceService service = service(port, audit);

        Map<String, Object> result = service.recordObservation(Map.of(
                "projectId", "p1",
                "agentId", "a1",
                "runId", "run-1",
                "completed", true,
                "normalizedUserGoal", "检查服务错误",
                "finalOutput", "证据充分的诊断结果".repeat(10),
                "toolEvidence", List.of("Prometheus"),
                "evidenceRefs", List.of(Map.of(
                        "resultId", "r1",
                        "outputHash", "h1"))),
                "SUCCESSFUL_DIAGNOSTIC_PATTERN");

        assertEquals(false, result.get("newObservation"));
        verify(port, never()).upsertCluster(
                org.mockito.ArgumentMatchers.any());
        verify(audit, never()).recordObservation(
                org.mockito.ArgumentMatchers.any());
    }

    private OpsSkillExperienceService service(
            SkillExperiencePort port,
            SkillExperienceAuditPort audit) {
        return new OpsSkillExperienceService(
                new SkillExperienceApplicationService(
                        port,
                        audit,
                        directTransactionPort(),
                        new SkillExperienceObservationPolicy(),
                        (project,run) -> new cn.lgs.orbisops.domain.skill.model.VerifiedTaskOutcome(
                                project,run,"synthetic-task",1,"synthetic-acceptance","synthetic-condition")));
    }

    private SkillTransactionPort directTransactionPort() {
        return new SkillTransactionPort() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
