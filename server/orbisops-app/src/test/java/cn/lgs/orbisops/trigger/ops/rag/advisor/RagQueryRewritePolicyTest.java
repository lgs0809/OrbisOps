package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQueryRewritePolicyTest {

    private final RagQueryRewritePolicy policy = new RagQueryRewritePolicy();

    @Test
    void shouldPreserveRuleExpansionOrderAndVocabulary() {
        String query = "慢SQL 错误 Prometheus 指标 架构图";
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("rule")
                .llmQueryRewriteEnabled(false)
                .llmQueryRewriteMaxQueries(8)
                .build();

        RagQueryRewriteDecision decision = policy.initialDecision(query, Map.of(), settings);

        assertTrue(decision.rewriteEnabled());
        assertFalse(decision.llmEligible());
        assertEquals(5, decision.queries().size());
        assertEquals(query, decision.queries().get(0));
        assertEquals(query + " query_time rows_examined rows_sent full table scan missing index lock wait explain",
                decision.queries().get(1));
        assertEquals(query + " error exception stacktrace traceId root cause rollback timeout failed",
                decision.queries().get(2));
        assertEquals(query + " prometheus metric qps latency error_rate cpu memory saturation alert",
                decision.queries().get(3));
        assertEquals(query + " image screenshot diagram architecture flow chart evidence description",
                decision.queries().get(4));
    }

    @Test
    void shouldPreserveRewriteDisableAndBlankQueryDecision() {
        RagRetrievalSettings settings = new RagRetrievalSettings();

        RagQueryRewriteDecision disabled = policy.initialDecision(
                "慢SQL",
                Map.of("qa_query_rewrite_enabled", false),
                settings);
        RagQueryRewriteDecision blank = policy.initialDecision("\u2003", Map.of(), settings);

        assertFalse(disabled.rewriteEnabled());
        assertFalse(disabled.llmEligible());
        assertEquals(List.of("慢SQL"), disabled.queries());
        assertFalse(blank.rewriteEnabled());
        assertFalse(blank.llmEligible());
        assertEquals(List.of("\u2003"), blank.queries());
    }

    @Test
    void shouldClampRuleQueryCountAndFallbackMalformedValue() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("rule")
                .llmQueryRewriteEnabled(false)
                .llmQueryRewriteMaxQueries(3)
                .build();
        String query = "慢SQL 错误 Prometheus 图片";

        RagQueryRewriteDecision lowerClamp = policy.initialDecision(
                query,
                Map.of("qa_query_rewrite_max_queries", 0),
                settings);
        RagQueryRewriteDecision configured = policy.initialDecision(
                query,
                Map.of("qa_query_rewrite_max_queries", "invalid"),
                settings);
        RagQueryRewriteDecision upperClamp = policy.initialDecision(
                query,
                Map.of("qa_query_rewrite_max_queries", 100),
                settings);

        assertEquals(1, lowerClamp.queries().size());
        assertEquals(3, configured.queries().size());
        assertEquals(5, upperClamp.queries().size());
    }

    @Test
    void shouldUseLlmModeOnlyWhenLlmRewriteIsEnabled() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("llm")
                .llmQueryRewriteEnabled(false)
                .build();

        RagQueryRewriteDecision configuredDisabled = policy.initialDecision("普通问题", Map.of(), settings);
        RagQueryRewriteDecision contextEnabled = policy.initialDecision(
                "普通问题",
                Map.of("qa_llm_query_rewrite_enabled", true),
                settings);
        RagQueryRewriteDecision contextMode = policy.initialDecision(
                "普通问题",
                Map.of(
                        "qa_llm_query_rewrite_enabled", true,
                        "qa_query_rewrite_mode", "LLM"),
                new RagRetrievalSettings());

        assertFalse(configuredDisabled.llmEligible());
        assertTrue(contextEnabled.llmEligible());
        assertTrue(contextMode.llmEligible());
    }

    @Test
    void shouldPreserveHybridComplexityRulesAndMinimumLength() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("hybrid")
                .llmQueryRewriteEnabled(true)
                .llmQueryRewriteMinChars(18)
                .build();

        assertFalse(policy.initialDecision("怎么排查", Map.of(), settings).llmEligible());
        assertTrue(policy.initialDecision("请帮我分析最近示例下单接口为什么突然变慢", Map.of(), settings).llmEligible());
        assertTrue(policy.initialDecision("why slow", Map.of(), settings).llmEligible());
        assertTrue(policy.initialDecision("这个", Map.of(), settings).llmEligible());
        assertFalse(policy.initialDecision("普通知识解释", Map.of(), settings).llmEligible());

        RagQueryRewriteDecision contextOverride = policy.initialDecision(
                "怎么排查",
                Map.of("qa_llm_query_rewrite_min_chars", 4),
                settings);
        assertTrue(contextOverride.llmEligible());
    }

    @Test
    void shouldTreatBlankModeAsRuleAndUnsupportedModeAsNonLlm() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("hybrid")
                .llmQueryRewriteEnabled(true)
                .build();

        RagQueryRewriteDecision blank = policy.initialDecision(
                "why slow",
                Map.of("qa_query_rewrite_mode", "\u2003"),
                settings);
        RagQueryRewriteDecision unsupported = policy.initialDecision(
                "why slow",
                Map.of("qa_query_rewrite_mode", "unsupported"),
                settings);

        assertFalse(blank.llmEligible());
        assertFalse(unsupported.llmEligible());
    }

    @Test
    void shouldPreserveLowRecallRetryEligibilityGates() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("hybrid")
                .llmQueryRewriteEnabled(true)
                .llmQueryRewriteOnLowRecall(true)
                .llmQueryRewriteLowRecallMinCandidates(2)
                .build();

        assertTrue(policy.shouldRetryAfterLowRecall("普通问题", Map.of(), settings, false, 1));
        assertFalse(policy.shouldRetryAfterLowRecall("普通问题", Map.of(), settings, false, 2));
        assertFalse(policy.shouldRetryAfterLowRecall("普通问题", Map.of(), settings, true, 0));
        assertFalse(policy.shouldRetryAfterLowRecall("\u2003", Map.of(), settings, false, 0));
        assertFalse(policy.shouldRetryAfterLowRecall(
                "普通问题",
                Map.of("qa_llm_query_rewrite_enabled", false),
                settings,
                false,
                0));
        assertFalse(policy.shouldRetryAfterLowRecall(
                "普通问题",
                Map.of("qa_llm_query_rewrite_on_low_recall", false),
                settings,
                false,
                0));
        assertFalse(policy.shouldRetryAfterLowRecall(
                "普通问题",
                Map.of("qa_query_rewrite_mode", "rule"),
                settings,
                false,
                0));
    }

    @Test
    void shouldClampLowRecallMinimumToAtLeastOneAndFallbackMalformedValue() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .queryRewriteMode("llm")
                .llmQueryRewriteEnabled(true)
                .llmQueryRewriteOnLowRecall(true)
                .llmQueryRewriteLowRecallMinCandidates(3)
                .build();

        assertTrue(policy.shouldRetryAfterLowRecall(
                "query",
                Map.of("qa_llm_query_rewrite_low_recall_min_candidates", -9),
                settings,
                false,
                0));
        assertFalse(policy.shouldRetryAfterLowRecall(
                "query",
                Map.of("qa_llm_query_rewrite_low_recall_min_candidates", -9),
                settings,
                false,
                1));
        assertTrue(policy.shouldRetryAfterLowRecall(
                "query",
                Map.of("qa_llm_query_rewrite_low_recall_min_candidates", "invalid"),
                settings,
                false,
                2));
        assertFalse(policy.shouldRetryAfterLowRecall(
                "query",
                Map.of("qa_llm_query_rewrite_low_recall_min_candidates", "invalid"),
                settings,
                false,
                3));
    }

    @Test
    void decisionQueriesMustBeDefensivelyCopied() {
        RagQueryRewriteDecision decision = new RagQueryRewriteDecision(
                new java.util.ArrayList<>(List.of("query")),
                true,
                false);

        boolean immutable;
        try {
            decision.queries().add("other");
            immutable = false;
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }

        assertTrue(immutable);
    }
}
