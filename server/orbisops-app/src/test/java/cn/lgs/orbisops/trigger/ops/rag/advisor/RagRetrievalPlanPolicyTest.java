package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalPlanPolicyTest {

    private final RagRetrievalPlanPolicy policy = new RagRetrievalPlanPolicy();

    @Test
    void shouldKeepProviderNeutralDefaultsWithoutRerankExpansion() {
        RagRetrievalSettings settings = new RagRetrievalSettings();

        RagRetrievalPlan plan = policy.resolve("普通知识查询", Map.of(), settings, 4);

        assertEquals("vector", plan.mode());
        assertEquals(4, plan.vectorTopK());
        assertEquals(6, plan.bm25TopK());
        assertEquals(6, plan.finalTopK());
        assertEquals(12000, plan.maxContextChars());
        assertFalse(plan.rerankEnabled());
        assertEquals("none", plan.rerankProvider());
        assertEquals("", plan.rerankBaseUrl());
        assertEquals("v1/rerank", plan.rerankPath());
        assertEquals("", plan.rerankModel());
        assertEquals(20, plan.rerankCandidateTopK());
        assertEquals(6, plan.rerankTopN());
        assertEquals(1200, plan.rerankMaxDocChars());
    }

    @Test
    void shouldUseFrameworkTopKWhenBuilderSettingsAreNonPositive() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .topK(0)
                .vectorTopK(0)
                .bm25TopK(0)
                .finalTopK(0)
                .maxContextChars(0)
                .rerankEnabled(false)
                .rerankCandidateTopK(0)
                .rerankTopN(0)
                .rerankMaxDocChars(0)
                .build();

        RagRetrievalPlan plan = policy.resolve("普通知识查询", Map.of(), settings, 7);

        assertEquals("vector", plan.mode());
        assertEquals(7, plan.vectorTopK());
        assertEquals(7, plan.bm25TopK());
        assertEquals(7, plan.finalTopK());
        assertEquals(12000, plan.maxContextChars());
        assertFalse(plan.rerankEnabled());
        assertEquals("none", plan.rerankProvider());
        assertEquals(21, plan.rerankCandidateTopK());
        assertEquals(7, plan.rerankTopN());
        assertEquals(1200, plan.rerankMaxDocChars());
    }

    @Test
    void shouldApplyContextOverridesAndClampEveryNumericBoundary() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("hybrid")
                .vectorTopK(8)
                .bm25TopK(8)
                .finalTopK(8)
                .maxContextChars(8000)
                .rerankEnabled(true)
                .rerankProvider("cohere")
                .rerankCandidateTopK(18)
                .rerankTopN(8)
                .rerankMaxDocChars(1200)
                .build();
        Map<String, Object> context = Map.ofEntries(
                Map.entry("qa_retrieval_mode", "unsupported"),
                Map.entry("qa_dynamic_search", "true"),
                Map.entry("qa_vector_top_k", "0"),
                Map.entry("qa_bm25_top_k", "99"),
                Map.entry("qa_final_top_k", "-5"),
                Map.entry("qa_max_context_chars", "999"),
                Map.entry("qa_rerank_enabled", "false"),
                Map.entry("qa_rerank_provider", "unsupported-provider"),
                Map.entry("qa_rerank_base_url", "http://rerank.local"),
                Map.entry("qa_rerank_path", "/custom/rerank"),
                Map.entry("qa_rerank_model", "custom-model"),
                Map.entry("qa_rerank_candidate_top_k", "100"),
                Map.entry("qa_rerank_top_n", "100"),
                Map.entry("qa_rerank_max_doc_chars", "100"));

        RagRetrievalPlan plan = policy.resolve("traceId 查询", context, settings, 4);

        assertEquals("vector", plan.mode());
        assertEquals(1, plan.vectorTopK());
        assertEquals(30, plan.bm25TopK());
        assertEquals(1, plan.finalTopK());
        assertEquals(1000, plan.maxContextChars());
        assertFalse(plan.rerankEnabled());
        assertEquals("none", plan.rerankProvider());
        assertEquals("http://rerank.local", plan.rerankBaseUrl());
        assertEquals("/custom/rerank", plan.rerankPath());
        assertEquals("custom-model", plan.rerankModel());
        assertEquals(50, plan.rerankCandidateTopK());
        assertEquals(1, plan.rerankTopN());
        assertEquals(300, plan.rerankMaxDocChars());
    }

    @Test
    void shouldPreserveDynamicAutoModeInferencePriority() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("auto")
                .dynamicSearch(true)
                .vectorTopK(4)
                .bm25TopK(6)
                .finalTopK(6)
                .rerankEnabled(false)
                .build();

        assertEquals("hybrid", policy.resolve("查看 traceId 对应的架构图", Map.of(), settings, 4).mode());
        assertEquals("bm25", policy.resolve("精确包含错误码 ERR_LOCK_001", Map.of(), settings, 4).mode());
        assertEquals("hybrid", policy.resolve("分析 ERROR 日志和报警", Map.of(), settings, 4).mode());
        assertEquals("vector", policy.resolve("解释分布式锁原理", Map.of(), settings, 4).mode());
    }

    @Test
    void shouldFallbackAutoToVectorWhenDynamicSearchIsDisabled() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("auto")
                .dynamicSearch(false)
                .rerankEnabled(false)
                .build();

        RagRetrievalPlan configured = policy.resolve("精确查 traceId", Map.of(), settings, 4);
        RagRetrievalPlan overridden = policy.resolve(
                "精确查 traceId",
                Map.of("qa_dynamic_search", true),
                settings,
                4);

        assertEquals("vector", configured.mode());
        assertEquals("bm25", overridden.mode());
    }

    @Test
    void shouldExpandRecallCandidatesWhenRerankIsEnabled() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("HYBRID")
                .vectorTopK(2)
                .bm25TopK(3)
                .finalTopK(5)
                .maxContextChars(90000)
                .rerankEnabled(true)
                .rerankProvider("VOYAGE")
                .rerankCandidateTopK(40)
                .rerankTopN(9)
                .rerankMaxDocChars(7000)
                .build();

        RagRetrievalPlan plan = policy.resolve("查询", Map.of(), settings, 4);

        assertEquals("hybrid", plan.mode());
        assertEquals(20, plan.vectorTopK());
        assertEquals(30, plan.bm25TopK());
        assertEquals(5, plan.finalTopK());
        assertEquals(50000, plan.maxContextChars());
        assertEquals("voyage", plan.rerankProvider());
        assertEquals(40, plan.rerankCandidateTopK());
        assertEquals(5, plan.rerankTopN());
        assertEquals(6000, plan.rerankMaxDocChars());
    }

    @Test
    void shouldFallbackMalformedContextValuesToConfiguredDefaults() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("bm25")
                .vectorTopK(9)
                .bm25TopK(11)
                .finalTopK(7)
                .maxContextChars(16000)
                .rerankEnabled(false)
                .rerankProvider("voyage")
                .rerankCandidateTopK(15)
                .rerankTopN(6)
                .rerankMaxDocChars(1500)
                .build();
        Map<String, Object> context = Map.of(
                "qa_vector_top_k", "invalid",
                "qa_bm25_top_k", "invalid",
                "qa_final_top_k", "invalid",
                "qa_max_context_chars", "invalid",
                "qa_rerank_candidate_top_k", "invalid",
                "qa_rerank_top_n", "invalid",
                "qa_rerank_max_doc_chars", "invalid");

        RagRetrievalPlan plan = policy.resolve("查询", context, settings, 4);

        assertEquals(9, plan.vectorTopK());
        assertEquals(11, plan.bm25TopK());
        assertEquals(7, plan.finalTopK());
        assertEquals(16000, plan.maxContextChars());
        assertEquals(15, plan.rerankCandidateTopK());
        assertEquals(6, plan.rerankTopN());
        assertEquals(1500, plan.rerankMaxDocChars());
    }

    @Test
    void shouldTreatUnicodeWhitespaceModeAndProviderAsBlank() {
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .retrievalMode("hybrid")
                .rerankEnabled(false)
                .rerankProvider("cohere")
                .build();

        RagRetrievalPlan plan = policy.resolve(
                "查询",
                Map.of(
                        "qa_retrieval_mode", "\u2003\u2003",
                        "qa_rerank_provider", "\u2003"),
                settings,
                4);

        assertEquals("vector", plan.mode());
        assertEquals("none", plan.rerankProvider());
    }
}
