package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineDecision;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillEvolutionPipelineServiceTest {

    @Test
    void legacyFacadeMapsRuntimeFactsIntoTypedApplicationRequest() {
        SkillEvolutionApplicationService application =
                mock(SkillEvolutionApplicationService.class);
        when(application.decide(any())).thenReturn(
                new SkillEvolutionPipelineDecision(
                        "SKIPPED",
                        "SKIP_WAITING_FOR_TRUSTED_EVIDENCE",
                        "",
                        "",
                        "{\"status\":\"SKIPPED\",\"reasonCode\":\"SKIP_WAITING_FOR_TRUSTED_EVIDENCE\"}",
                        "SKIPPED"));
        OpsSkillEvolutionPipelineService pipeline =
                new OpsSkillEvolutionPipelineService(application);

        Map<String, Object> result = pipeline.process(Map.of(
                "projectId", "demo-project",
                "agentId", "ops",
                "runId", "run-1",
                "sessionId", "session-1",
                "triggerReason", "USER_EXPLICIT_REMEMBER",
                "completed", true,
                "failedThenRecovered", true,
                "toolEvidence", List.of("tool-result"),
                "evidenceRefs", List.of(Map.of(
                        "resultId", "r1",
                        "outputHash", "h1")),
                "finalOutput", "已根据错误率和日志完成定位、验证与回滚准备。"));

        ArgumentCaptor<SkillEvolutionPipelineRequest> request =
                ArgumentCaptor.forClass(SkillEvolutionPipelineRequest.class);
        verify(application).decide(request.capture());
        assertEquals("SKIPPED", result.get("status"));
        assertEquals(
                "SKIP_WAITING_FOR_TRUSTED_EVIDENCE",
                result.get("reasonCode"));
        assertEquals("demo-project", request.getValue().projectId());
        assertEquals(1, request.getValue().summary().evidenceReferences().size());
        assertTrue(request.getValue().opportunity().failedThenRecovered());
    }

    @Test
    void legacyFacadeUsesTypedDecisionWhenPayloadCannotBeDecoded() {
        SkillEvolutionApplicationService application =
                mock(SkillEvolutionApplicationService.class);
        when(application.decide(any())).thenReturn(
                new SkillEvolutionPipelineDecision(
                        "CANDIDATE",
                        "ENOUGH_SIGNAL",
                        "skill-1",
                        "skill-0",
                        "not-json",
                        "CANDIDATE"));
        OpsSkillEvolutionPipelineService pipeline =
                new OpsSkillEvolutionPipelineService(application);

        Map<String, Object> result = pipeline.process(Map.of());

        assertEquals("CANDIDATE", result.get("status"));
        assertEquals("ENOUGH_SIGNAL", result.get("reasonCode"));
        assertEquals("skill-1", result.get("targetSkillId"));
        assertEquals("skill-0", result.get("matchedSkillId"));
    }
}
