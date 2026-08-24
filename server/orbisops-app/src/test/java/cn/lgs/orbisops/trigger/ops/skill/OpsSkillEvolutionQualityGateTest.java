package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void missingModelNeverCreatesARuleCandidateEvenWithDetailedEvidence() {
        var client=mock(OpsSkillAuthoringModelClient.class);
        when(client.available()).thenReturn(false);
        var agent=new OpsSkillAuthoringAgent(client);
        for(var input:List.of(Map.<String,Object>of(),Map.<String,Object>of(
                "runId","run-1","normalizedUserGoal","排查下单失败","finalOutput",reusableReport(),
                "toolEvidence",List.of("prometheus","elasticsearch"),
                "evidenceRefs",List.of(Map.of("resultId","result-1","outputHash","hash-1"))))) {
            var error=assertThrows(IllegalStateException.class,()->agent.author(input));
            assertEquals("SKILL_EVOLUTION_MODEL_UNAVAILABLE",error.getMessage());
        }
        org.mockito.Mockito.verify(client,org.mockito.Mockito.never()).generate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test void invalidModelCandidateNeverFallsBackToRuleGeneration() {
        var client=mock(OpsSkillAuthoringModelClient.class);when(client.available()).thenReturn(true);
        when(client.generate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.alibaba.fastjson.JSON.parseObject("{\"patchType\":\"CREATE_SKILL\",\"changes\":[]}"));
        assertEquals("SKILL_AUTHORING_MODEL_INVALID",assertThrows(IllegalStateException.class,
                ()->new OpsSkillAuthoringAgent(client).author(Map.of("finalOutput",reusableReport()))).getMessage());
    }

    @Test void authorPreservesTheSelectedTargetInsteadOfAllowingLaterRebinding() {
        var client=mock(OpsSkillAuthoringModelClient.class);when(client.available()).thenReturn(true);
        when(client.generate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.alibaba.fastjson.JSON.parseObject("{\"patchType\":\"UPDATE_DIAGNOSTIC_RECIPE\",\"targetSkillId\":\"frozen-project-skill\",\"reason\":\"SYNTHETIC qualified extension\",\"changes\":[{\"section\":\"diagnosticRecipe\",\"key\":\"fixture\",\"value\":\"SYNTHETIC\"}]}"));
        assertEquals("frozen-project-skill",new OpsSkillAuthoringAgent(client).author(Map.of()).get("targetSkillId"));
    }

    private String reusableReport() {
        return "根因是订单依赖超时。先通过 Prometheus 确认十分钟错误率，再查询 Elasticsearch 日志样本，"
                + "证据显示依赖接口持续超时；然后验证发布记录和配置差异，修复后重新验证指标并保留回滚步骤。";
    }
}
