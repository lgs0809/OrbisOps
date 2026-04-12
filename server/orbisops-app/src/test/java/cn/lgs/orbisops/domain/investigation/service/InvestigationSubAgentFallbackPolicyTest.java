package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentQueryDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationSubAgentReviewDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationSubAgentFallbackPolicyTest {

    private final InvestigationSubAgentFallbackPolicy policy =
            new InvestigationSubAgentFallbackPolicy();

    @Test
    void runtimeFilterUsesHybridAndExactFilters() {
        InvestigationSubAgentQueryDecision decision = policy.query(
                new InvestigationSubAgentFallbackPolicy.QueryInput(
                        "elasticsearch",
                        30,
                        "5m",
                        false,
                        true,
                        "沿用默认参数"));

        assertFalse(decision.llmGenerated());
        assertEquals("只读兜底策略：沿用默认参数", decision.reason());
        assertEquals(Integer.valueOf(30), decision.rangeMinutes());
        assertEquals("5m", decision.promWindow());
        assertEquals(Boolean.FALSE, decision.includeRecentLogs());
        assertEquals("hybrid", decision.retrievalMode());
        assertEquals("elasticsearch 默认排障查询", decision.queryFocus());
        assertEquals(Boolean.TRUE, decision.requireExactFilters());
        assertEquals(List.of("真实查询结果", "证据缺口", "是否需要主 Agent 调整"),
                decision.expectedEvidence());
    }

    @Test
    void noRuntimeFilterUsesAutoAndPreservesNullableValues() {
        InvestigationSubAgentQueryDecision decision = policy.query(
                new InvestigationSubAgentFallbackPolicy.QueryInput(
                        "rag",
                        null,
                        null,
                        null,
                        false,
                        "默认"));

        assertNull(decision.rangeMinutes());
        assertNull(decision.promWindow());
        assertNull(decision.includeRecentLogs());
        assertEquals("auto", decision.retrievalMode());
        assertEquals(Boolean.FALSE, decision.requireExactFilters());
    }

    @Test
    void nullSourceAndReasonKeepLegacyStringConcatenation() {
        InvestigationSubAgentQueryDecision decision = policy.query(
                new InvestigationSubAgentFallbackPolicy.QueryInput(
                        null,
                        15,
                        "5m",
                        true,
                        false,
                        null));

        assertEquals("只读兜底策略：null", decision.reason());
        assertEquals("null 默认排障查询", decision.queryFocus());
        assertThrows(UnsupportedOperationException.class,
                () -> decision.expectedEvidence().add("new"));
    }

    @Test
    void reviewPrependsDegradationEvidenceAndPreservesDecisionFields() {
        InvestigationSubAgentReviewDecision decision = policy.review(
                new InvestigationSubAgentFallbackPolicy.ReviewInput(
                        "INSUFFICIENT",
                        "证据不足",
                        List.of("缺少指标"),
                        List.of("扩大窗口"),
                        true,
                        0.35D,
                        "模型不可用"));

        assertFalse(decision.llmGenerated());
        assertEquals("INSUFFICIENT", decision.status());
        assertEquals("证据不足", decision.summary());
        assertEquals(List.of(
                "LLM 子 Agent 复盘不可用：模型不可用",
                "缺少指标"), decision.gaps());
        assertEquals(List.of(
                "已保留真实查询 observation，并使用确定性规则判断证据状态。",
                "扩大窗口"), decision.suggestedAdjustments());
        assertEquals(Boolean.TRUE, decision.shouldRetry());
        assertEquals(0.35D, decision.confidence());
        decision.gaps().add("可追加");
        decision.suggestedAdjustments().add("可追加");
        assertTrue(decision.gaps().contains("可追加"));
    }

    @Test
    void reviewSupportsNullListsAndNullableFieldsWithoutNormalization() {
        InvestigationSubAgentReviewDecision decision = policy.review(
                new InvestigationSubAgentFallbackPolicy.ReviewInput(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertNull(decision.status());
        assertNull(decision.summary());
        assertEquals(List.of("LLM 子 Agent 复盘不可用：null"), decision.gaps());
        assertEquals(List.of(
                "已保留真实查询 observation，并使用确定性规则判断证据状态。"),
                decision.suggestedAdjustments());
        assertNull(decision.shouldRetry());
        assertNull(decision.confidence());
    }
}
