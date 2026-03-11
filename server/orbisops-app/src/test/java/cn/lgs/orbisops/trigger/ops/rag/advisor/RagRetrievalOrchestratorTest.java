package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalOrchestratorTest {

    @Test
    void shouldUseContextFilterOverrideAndExecuteFixedPipeline() {
        AtomicReference<String> filter = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        RagRecallCoordinator recall = recall((retrievalQuery, filterExpression, topK) -> {
            query.set(retrievalQuery);
            filter.set(filterExpression);
            return List.of(document("chunk", "锁单失败排查"));
        });
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .filterExpression("knowledge == 'configured'")
                .build();
        RagRetrievalOrchestrator orchestrator = orchestrator(settings, recall, noCallRewriteProtocol());
        Map<String, Object> context = mutableContext(
                "qa_filter_expression", "knowledge == 'override'",
                "qa_query_rewrite_enabled", false,
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false);

        List<Document> documents = orchestrator.retrieve("锁单失败", context, plan(5));

        assertEquals("锁单失败", query.get());
        assertEquals("knowledge == 'override'", filter.get());
        assertEquals(List.of("chunk"), documents.stream().map(Document::getId).toList());
        assertFalse(context.containsKey("qa_low_recall_rewrite_applied"));
    }

    @Test
    void shouldFallbackToConfiguredFilterWhenOverrideIsNullOrBlank() {
        List<String> filters = new ArrayList<>();
        RagRecallCoordinator recall = recall((query, filterExpression, topK) -> {
            filters.add(filterExpression);
            return List.of(document(query, query));
        });
        RagRetrievalSettings settings = RagRetrievalSettings.builder()
                .filterExpression("knowledge == 'configured'")
                .build();
        RagRetrievalOrchestrator orchestrator = orchestrator(settings, recall, noCallRewriteProtocol());

        orchestrator.retrieve("first", mutableContext(
                "qa_filter_expression", null,
                "qa_query_rewrite_enabled", false,
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false), plan(5));
        orchestrator.retrieve("second", mutableContext(
                "qa_filter_expression", "   ",
                "qa_query_rewrite_enabled", false,
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false), plan(5));

        assertEquals(List.of("knowledge == 'configured'", "knowledge == 'configured'"), filters);
    }

    @Test
    void shouldFanOutDeterministicRuleQueriesBeforeFusion() {
        List<String> queries = new ArrayList<>();
        RagRecallCoordinator recall = recall((query, filterExpression, topK) -> {
            queries.add(query);
            return List.of(document(String.valueOf(queries.size()), query));
        });
        RagRetrievalOrchestrator orchestrator = orchestrator(
                new RagRetrievalSettings(),
                recall,
                noCallRewriteProtocol());
        Map<String, Object> context = mutableContext(
                "qa_query_rewrite_enabled", true,
                "qa_query_rewrite_mode", "rule",
                "qa_llm_query_rewrite_enabled", false,
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false);

        List<Document> documents = orchestrator.retrieve("慢SQL错误", context, plan(10));

        assertEquals(3, queries.size());
        assertEquals("慢SQL错误", queries.get(0));
        assertTrue(queries.get(1).contains("query_time"));
        assertTrue(queries.get(2).contains("stacktrace"));
        assertEquals(queries, context.get("qa_rewrite_queries"));
        assertEquals(3, documents.size());
    }

    @Test
    void shouldRetryWithLlmRewriteAfterLowRecallAndReplaceCandidates() {
        List<String> queries = new ArrayList<>();
        AtomicReference<JSONObject> rewritePayload = new AtomicReference<>();
        RagRecallCoordinator recall = recall((query, filterExpression, topK) -> {
            queries.add(query);
            if ("expanded query".equals(query)) {
                return List.of(document("expanded", "expanded evidence"));
            }
            return List.of();
        });
        RagLlmQueryRewriteProtocol rewriteProtocol = new RagLlmQueryRewriteProtocol((url, body, apiKey, timeoutSeconds) -> {
            rewritePayload.set(JSONObject.parseObject(body));
            return "{\"choices\":[{\"message\":{\"content\":\"{\\\"queries\\\":[\\\"expanded query\\\"]}\"}}]}";
        });
        RagRetrievalOrchestrator orchestrator = orchestrator(
                new RagRetrievalSettings(),
                recall,
                rewriteProtocol);
        Map<String, Object> context = mutableContext(
                "qa_query_rewrite_enabled", true,
                "qa_query_rewrite_mode", "hybrid",
                "qa_llm_query_rewrite_enabled", true,
                "qa_llm_query_rewrite_on_low_recall", true,
                "qa_llm_query_rewrite_low_recall_min_candidates", 2,
                "qa_llm_query_rewrite_base_url", "https://rewrite.example.com",
                "qa_llm_query_rewrite_api_key", "secret",
                "qa_llm_query_rewrite_path", "/v1/chat/completions",
                "qa_llm_query_rewrite_model", "rewrite-model",
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false);

        List<Document> documents = orchestrator.retrieve("库存查询", context, plan(5));

        assertEquals(List.of("库存查询", "库存查询", "expanded query"), queries);
        assertEquals(List.of("expanded"), documents.stream().map(Document::getId).toList());
        assertEquals(true, context.get("qa_low_recall_rewrite_applied"));
        assertEquals(true, context.get("qa_llm_query_rewrite_applied"));
        assertEquals(List.of("库存查询", "expanded query"), context.get("qa_rewrite_queries"));
        JSONObject userPayload = JSONObject.parseObject(rewritePayload.get()
                .getJSONArray("messages")
                .getJSONObject(1)
                .getString("content"));
        assertTrue(userPayload.getBooleanValue("lowRecallRetry"));
    }

    @Test
    void shouldPropagateStrictLlmRewriteFailureWithDiagnostics() {
        RagRecallCoordinator recall = recall((query, filterExpression, topK) -> List.of());
        RagLlmQueryRewriteProtocol rewriteProtocol = new RagLlmQueryRewriteProtocol((url, body, apiKey, timeoutSeconds) -> {
            throw new IllegalStateException("rewrite unavailable");
        });
        RagRetrievalOrchestrator orchestrator = orchestrator(
                new RagRetrievalSettings(),
                recall,
                rewriteProtocol);
        Map<String, Object> context = mutableContext(
                "qa_query_rewrite_enabled", true,
                "qa_query_rewrite_mode", "llm",
                "qa_llm_query_rewrite_enabled", true,
                "qa_llm_query_rewrite_base_url", "https://rewrite.example.com",
                "qa_llm_query_rewrite_api_key", "secret",
                "qa_llm_query_rewrite_path", "/v1/chat/completions",
                "qa_llm_query_rewrite_model", "rewrite-model",
                "qa_query_rewrite_fail_on_degradation", true,
                "qa_mmr_enabled", false);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                orchestrator.retrieve("根因分析", context, plan(5)));

        assertTrue(failure.getMessage().startsWith("RAG LLM query rewrite 失败："));
        assertTrue(failure.getCause() != null);
        assertEquals(true, context.get("qa_llm_query_rewrite_degraded"));
        assertTrue(String.valueOf(context.get("qa_llm_query_rewrite_error")).contains("rewrite unavailable"));
    }

    @Test
    void shouldApplyFinalTopKAfterFusionRerankAndMmrStages() {
        RagRecallCoordinator recall = recall((query, filterExpression, topK) -> List.of(
                document("a", "alpha"),
                document("b", "beta"),
                document("c", "gamma")));
        RagRetrievalOrchestrator orchestrator = orchestrator(
                new RagRetrievalSettings(),
                recall,
                noCallRewriteProtocol());
        Map<String, Object> context = mutableContext(
                "qa_query_rewrite_enabled", false,
                "qa_mmr_enabled", false,
                "qa_exclude_memory_documents", false);

        List<Document> documents = orchestrator.retrieve("query", context, plan(2));

        assertEquals(List.of("a", "b"), documents.stream().map(Document::getId).toList());
    }

    private RagRetrievalOrchestrator orchestrator(RagRetrievalSettings settings,
                                                   RagRecallCoordinator recall,
                                                   RagLlmQueryRewriteProtocol rewriteProtocol) {
        return new RagRetrievalOrchestrator(
                settings,
                recall,
                new RagReciprocalRankFusion(),
                new RagMmrDiversitySelector(),
                new RagRerankProtocol(settings, (uri, body, apiKey, timeoutSeconds) -> {
                    throw new AssertionError("rerank transport must not be called");
                }),
                new RagQueryRewritePolicy(),
                rewriteProtocol);
    }

    private RagRecallCoordinator recall(RepositorySearch repositorySearch) {
        return new RagRecallCoordinator(new RagRecallCoordinator.RepositoryVectorRecall() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<Document> search(String query, String filterExpression, int topK) throws Exception {
                return repositorySearch.search(query, filterExpression, topK);
            }
        }, null, null, null);
    }

    @FunctionalInterface
    private interface RepositorySearch {
        List<Document> search(String query, String filterExpression, int topK) throws Exception;
    }

    private RagLlmQueryRewriteProtocol noCallRewriteProtocol() {
        return new RagLlmQueryRewriteProtocol((url, body, apiKey, timeoutSeconds) -> {
            throw new AssertionError("rewrite transport must not be called");
        });
    }

    private Document document(String id, String text) {
        return new Document(id, text, Map.of("source", id + ".md"));
    }

    private RagRetrievalPlan plan(int finalTopK) {
        return new RagRetrievalPlan(
                "vector",
                10,
                10,
                finalTopK,
                12000,
                false,
                "none",
                null,
                null,
                null,
                10,
                finalTopK,
                1200);
    }

    private Map<String, Object> mutableContext(Object... pairs) {
        Map<String, Object> context = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            context.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return context;
    }
}
