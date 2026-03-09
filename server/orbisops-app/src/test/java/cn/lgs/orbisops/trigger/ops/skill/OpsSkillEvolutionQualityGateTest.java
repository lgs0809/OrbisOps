package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillEvolutionQualityGateTest {

    @Test
    void completedRunWithoutTrustedEvidenceIsNotAReusablePattern() {
        OpsSkillOpportunityDetector detector = new OpsSkillOpportunityDetector();

        String result = detector.detect(Map.of(
                "completed", true,
                "toolEvidence", List.of("TOOL_CALL_SUCCEEDED"),
                "evidenceRefs", List.of(),
                "finalOutput", reusableReport()));

        assertEquals("NO_REUSABLE_PATTERN", result);
    }

    @Test
    void completedRunWithTrustedEvidenceCanBecomeDiagnosticPattern() {
        OpsSkillOpportunityDetector detector = new OpsSkillOpportunityDetector();

        String result = detector.detect(Map.of(
                "completed", true,
                "toolEvidence", List.of("prometheus", "elasticsearch"),
                "evidenceRefs", List.of(Map.of("resultId", "result-1", "outputHash", "hash-1")),
                "finalOutput", reusableReport()));

        assertEquals("SUCCESSFUL_DIAGNOSTIC_PATTERN", result);
    }

    @Test
    void deterministicAuthoringDoesNotCreateGenericSkillWithoutEvidence() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.available()).thenReturn(false);
        OpsSkillAuthoringAgent agent = new OpsSkillAuthoringAgent(llmClient);

        Map<String, Object> candidate = agent.author(Map.of(
                "normalizedUserGoal", "排查订单问题",
                "toolEvidence", List.of(),
                "evidenceRefs", List.of()));

        assertEquals("NO_CHANGE", candidate.get("patchType"));
        assertEquals(List.of(), candidate.get("changes"));
    }

    @Test
    void deterministicAuthoringUsesStableSpecificKeyInsteadOfSharedFallback() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.available()).thenReturn(false);
        OpsSkillAuthoringAgent agent = new OpsSkillAuthoringAgent(llmClient);

        Map<String, Object> candidate = agent.author(Map.of(
                "runId", "run-1",
                "normalizedUserGoal", "排查下单失败",
                "finalOutput", reusableReport(),
                "toolEvidence", List.of("prometheus", "elasticsearch"),
                "evidenceRefs", List.of(Map.of("resultId", "result-1", "outputHash", "hash-1"))));

        Map<?, ?> change = (Map<?, ?>) ((List<?>) candidate.get("changes")).get(0);
        assertEquals("UPDATE_DIAGNOSTIC_RECIPE", candidate.get("patchType"));
        assertFalse("run-derived-recipe".equals(change.get("key")));
        assertFalse(String.valueOf(change.get("key")).isBlank());
        List<?> artifacts = (List<?>) candidate.get("artifacts");
        assertEquals(2, artifacts.size());
        assertEquals("resources/diagnostic-recipe.json", ((Map<?, ?>) artifacts.get(0)).get("path"));
        assertEquals("evals/regression-cases.json", ((Map<?, ?>) artifacts.get(1)).get("path"));
    }

    private String reusableReport() {
        return "根因是订单依赖超时。先通过 Prometheus 确认十分钟错误率，再查询 Elasticsearch 日志样本，"
                + "证据显示依赖接口持续超时；然后验证发布记录和配置差异，修复后重新验证指标并保留回滚步骤。";
    }
}
