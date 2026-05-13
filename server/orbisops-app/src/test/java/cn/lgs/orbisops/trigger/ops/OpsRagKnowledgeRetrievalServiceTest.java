package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRagKnowledgeRetrievalServiceTest {

    @Test
    void retrieveBuildsSearchConfigContextAndReturnsDiagnostics() throws Exception {
        AtomicReference<OpsRagKnowledgeRetrievalService.Input> capturedInput = new AtomicReference<>();
        AtomicReference<SearchRequest> capturedSearch = new AtomicReference<>();
        AtomicReference<RagRetrievalSettings> capturedConfig = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedContext = new AtomicReference<>();
        AtomicInteger cancellationChecks = new AtomicInteger();
        List<Document> documents = List.of(new Document(
                "doc-1",
                "故障处理 SOP",
                Map.of("source", "runbook.md")));
        OpsRagKnowledgeRetrievalService service = new OpsRagKnowledgeRetrievalService(
                (input, searchRequest, config, context) -> {
                    capturedInput.set(input);
                    capturedSearch.set(searchRequest);
                    capturedConfig.set(config);
                    capturedContext.set(context);
                    context.put("qa_retrieval_mode", "hybrid");
                    context.put("qa_rewrite_queries", List.of("原始问题", "扩展问题"));
                    context.put("qa_llm_query_rewrite_error", "rewrite unavailable");
                    context.put("qa_rerank_error", "rerank unavailable");
                    return documents;
                });

        OpsRagKnowledgeRetrievalService.Result result = service.retrieve(
                new OpsRagKnowledgeRetrievalService.Input(
                        "如何排查锁单失败",
                        "auto",
                        "knowledge == 'demo-ops'",
                        false,
                        null,
                        null,
                        null,
                        settings(true, 40, 12)),
                cancellationChecks::incrementAndGet);

        assertEquals(2, cancellationChecks.get());
        assertEquals("如何排查锁单失败", capturedInput.get().query());
        assertEquals("auto", capturedInput.get().retrievalMode());
        assertTrue(capturedSearch.get() != null);

        RagRetrievalSettings config = capturedConfig.get();
        assertEquals("auto", config.getRetrievalMode());
        assertEquals(20, config.getVectorTopK());
        assertEquals(30, config.getBm25TopK());
        assertEquals(10, config.getFinalTopK());
        assertEquals(12_000, config.getMaxContextChars());
        assertTrue(config.isDynamicSearch());
        assertEquals("hybrid", config.getQueryRewriteMode());
        assertTrue(config.getLlmQueryRewriteEnabled());
        assertEquals("http://rewrite", config.getLlmQueryRewriteBaseUrl());
        assertEquals("rewrite-key", config.getLlmQueryRewriteApiKey());
        assertEquals("v1/chat/completions", config.getLlmQueryRewritePath());
        assertEquals("rewrite-model", config.getLlmQueryRewriteModel());
        assertEquals(4, config.getLlmQueryRewriteMaxQueries());
        assertEquals(3, config.getLlmQueryRewriteTimeoutSeconds());
        assertEquals(20, config.getLlmQueryRewriteMinChars());
        assertTrue(config.getLlmQueryRewriteOnLowRecall());
        assertEquals(2, config.getLlmQueryRewriteLowRecallMinCandidates());
        assertFalse(config.getRerankEnabled());
        assertEquals("cohere", config.getRerankProvider());
        assertEquals("http://rerank", config.getRerankBaseUrl());
        assertEquals("rerank-key", config.getRerankApiKey());
        assertEquals("v1/rerank", config.getRerankPath());
        assertEquals("rerank-model", config.getRerankModel());
        assertEquals(40, config.getRerankCandidateTopK());
        assertEquals(12, config.getRerankTopN());
        assertEquals(1_500, config.getRerankMaxDocChars());
        assertEquals("knowledge == 'demo-ops'", config.getFilterExpression());

        Map<String, Object> context = capturedContext.get();
        assertEquals("knowledge == 'demo-ops'", context.get("qa_filter_expression"));
        assertEquals("hybrid", context.get("qa_query_rewrite_mode"));
        assertEquals(Boolean.TRUE, context.get("qa_llm_query_rewrite_enabled"));
        assertEquals("http://rewrite", context.get("qa_llm_query_rewrite_base_url"));
        assertEquals("rewrite-key", context.get("qa_llm_query_rewrite_api_key"));
        assertEquals(4, context.get("qa_query_rewrite_max_queries"));
        assertEquals(3, context.get("qa_llm_query_rewrite_timeout_seconds"));
        assertEquals(20, context.get("qa_llm_query_rewrite_min_chars"));
        assertEquals(Boolean.TRUE, context.get("qa_llm_query_rewrite_on_low_recall"));
        assertEquals(2, context.get("qa_llm_query_rewrite_low_recall_min_candidates"));
        assertEquals(Boolean.TRUE, context.get("qa_fail_on_degradation"));
        assertEquals(Boolean.FALSE, context.get("qa_query_rewrite_fail_on_degradation"));
        assertEquals(Boolean.FALSE, context.get("qa_rerank_fail_on_degradation"));
        assertEquals(Boolean.FALSE, context.get("qa_rerank_enabled"));

        assertSame(documents, result.documents());
        assertEquals("hybrid", result.retrievalMode());
        assertEquals(List.of("原始问题", "扩展问题"), result.rewriteQueries());
        assertEquals("rewrite unavailable", result.queryRewriteError());
        assertEquals("rerank unavailable", result.rerankError());
    }

    @Test
    void retrievalFailureKeepsExceptionIdentityAndSkipsPostCheck() {
        IllegalStateException failure = new IllegalStateException("advisor failed");
        AtomicInteger cancellationChecks = new AtomicInteger();
        OpsRagKnowledgeRetrievalService service = new OpsRagKnowledgeRetrievalService(
                (input, searchRequest, config, context) -> {
                    throw failure;
                });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.retrieve(
                        new OpsRagKnowledgeRetrievalService.Input(
                                "query",
                                "bm25",
                                "knowledge == 'kb'",
                                true,
                                null,
                                null,
                                null,
                                settings(false, 2, 0)),
                        cancellationChecks::incrementAndGet));

        assertSame(failure, thrown);
        assertEquals(1, cancellationChecks.get());
    }

    private OpsRagKnowledgeRetrievalService.Settings settings(
            boolean rerankEnabled,
            int candidateTopK,
            int topN) {
        return new OpsRagKnowledgeRetrievalService.Settings(
                rerankEnabled,
                "cohere",
                "http://rerank",
                "rerank-key",
                "v1/rerank",
                "rerank-model",
                candidateTopK,
                topN,
                1_500,
                "hybrid",
                true,
                "http://rewrite",
                "rewrite-key",
                "v1/chat/completions",
                "rewrite-model",
                4,
                3,
                20,
                true,
                2,
                true);
    }
}
