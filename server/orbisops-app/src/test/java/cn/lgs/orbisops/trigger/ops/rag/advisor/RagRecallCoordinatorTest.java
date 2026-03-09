package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRecallCoordinatorTest {

    @Test
    void shouldFanOutHybridRecallInStableSourceOrderAndKeepSourceLocalRanks() {
        List<String> vectorQueries = new ArrayList<>();
        AtomicReference<String> multimodalQuery = new AtomicReference<>();
        AtomicReference<String> lexicalQuery = new AtomicReference<>();
        AtomicReference<String> lexicalFilter = new AtomicReference<>();
        AtomicReference<Integer> lexicalTopK = new AtomicReference<>();

        RagRecallCoordinator.RepositoryVectorRecall repositoryVectorRecall = new RagRecallCoordinator.RepositoryVectorRecall() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<Document> search(String query, String filterExpression, int topK) {
                vectorQueries.add(query + "|" + filterExpression + "|" + topK);
                if ("rewrite-1".equals(query)) {
                    return List.of(document("v1"), document("v2"));
                }
                return List.of(document("v3"));
            }
        };
        RagRecallCoordinator.MultimodalRecall multimodalRecall = new RagRecallCoordinator.MultimodalRecall() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public List<Document> search(String query, String filterExpression, int topK) {
                multimodalQuery.set(query + "|" + filterExpression + "|" + topK);
                return List.of(document("m1"));
            }
        };
        RagRecallCoordinator coordinator = new RagRecallCoordinator(
                repositoryVectorRecall,
                (query, context, topK) -> List.of(document("unexpected-spring")),
                multimodalRecall,
                (query, filterExpression, topK) -> {
                    lexicalQuery.set(query);
                    lexicalFilter.set(filterExpression);
                    lexicalTopK.set(topK);
                    return List.of(document("b1"), document("b2"));
                });

        List<RagRankedDocument> results = coordinator.recall(
                "original question",
                Map.of("qa_exclude_memory_documents", false),
                plan("hybrid"),
                "knowledge == 'ops'",
                List.of("rewrite-1", "rewrite-2"));

        assertEquals(List.of(
                "rewrite-1|knowledge == 'ops'|3",
                "rewrite-2|knowledge == 'ops'|3"), vectorQueries);
        assertEquals("original question|knowledge == 'ops'|3", multimodalQuery.get());
        assertEquals("rewrite-1 rewrite-2", lexicalQuery.get());
        assertEquals("knowledge == 'ops'", lexicalFilter.get());
        assertEquals(4, lexicalTopK.get());
        assertEquals(List.of("vector", "vector", "vector", "multimodal", "bm25", "bm25"),
                results.stream().map(RagRankedDocument::source).toList());
        assertEquals(List.of("v1", "v2", "v3", "m1", "b1", "b2"),
                results.stream().map(result -> result.document().getId()).toList());
        assertEquals(List.of(1, 2, 1, 1, 1, 2),
                results.stream().map(RagRankedDocument::rank).toList());
        assertEquals(1.0d / 61.0d, results.get(0).score(), 0.000000000001d);
        assertEquals(1.0d / 62.0d, results.get(1).score(), 0.000000000001d);
        assertEquals(1.0d / 61.0d, results.get(2).score(), 0.000000000001d);
    }

    @Test
    void shouldHonorModeGatesAndFallbackToOriginalQueryWhenRewriteQueriesAreEmpty() {
        AtomicInteger vectorCalls = new AtomicInteger();
        AtomicInteger multimodalCalls = new AtomicInteger();
        AtomicInteger lexicalCalls = new AtomicInteger();
        AtomicReference<String> lexicalQuery = new AtomicReference<>();

        RagRecallCoordinator.LexicalRecall lexicalRecall = (query, filterExpression, topK) -> {
            lexicalCalls.incrementAndGet();
            lexicalQuery.set(query);
            return List.of(document("lexical"));
        };
        RagRecallCoordinator coordinator = new RagRecallCoordinator(
                new RagRecallCoordinator.RepositoryVectorRecall() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public List<Document> search(String query, String filterExpression, int topK) {
                        vectorCalls.incrementAndGet();
                        return List.of(document("vector"));
                    }
                },
                null,
                new RagRecallCoordinator.MultimodalRecall() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public List<Document> search(String query, String filterExpression, int topK) {
                        multimodalCalls.incrementAndGet();
                        return List.of(document("multimodal"));
                    }
                },
                lexicalRecall);

        List<RagRankedDocument> bm25 = coordinator.recall(
                "original",
                Map.of("qa_exclude_memory_documents", false),
                plan("bm25"),
                null,
                List.of());

        assertEquals(0, vectorCalls.get());
        assertEquals(0, multimodalCalls.get());
        assertEquals(1, lexicalCalls.get());
        assertEquals("original", lexicalQuery.get());
        assertEquals(List.of("lexical"), bm25.stream().map(result -> result.document().getId()).toList());

        List<RagRankedDocument> vector = coordinator.recall(
                "original",
                Map.of("qa_exclude_memory_documents", false),
                plan("vector"),
                null,
                null);

        assertEquals(1, vectorCalls.get());
        assertEquals(1, multimodalCalls.get());
        assertEquals(1, lexicalCalls.get());
        assertEquals(List.of("vector", "multimodal"),
                vector.stream().map(result -> result.document().getId()).toList());
    }

    @Test
    void shouldFallbackToSpringVectorRecallUnlessStrictDegradationIsRequested() {
        AtomicInteger springCalls = new AtomicInteger();
        RagRecallCoordinator coordinator = new RagRecallCoordinator(
                new RagRecallCoordinator.RepositoryVectorRecall() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public List<Document> search(String query, String filterExpression, int topK) {
                        throw new IllegalStateException("repository unavailable");
                    }
                },
                (query, context, topK) -> {
                    springCalls.incrementAndGet();
                    return List.of(document("spring-fallback"));
                },
                null,
                null);

        List<RagRankedDocument> degraded = coordinator.recall(
                "query",
                Map.of("qa_exclude_memory_documents", false),
                plan("vector"),
                null,
                List.of("query"));

        assertEquals(1, springCalls.get());
        assertEquals("spring-fallback", degraded.get(0).document().getId());

        IllegalStateException strictFailure = assertThrows(IllegalStateException.class, () -> coordinator.recall(
                "query",
                Map.of(
                        "qa_fail_on_degradation", true,
                        "qa_exclude_memory_documents", false),
                plan("vector"),
                null,
                List.of("query")));

        assertTrue(strictFailure.getMessage().contains("RAG vector halfvec/HNSW 检索失败"));
        assertEquals(1, springCalls.get());
    }

    @Test
    void shouldExcludeOpsChatMemoryByDefaultAndAllowExplicitOptOut() {
        Document knowledge = new Document("knowledge", "normal", Map.of("knowledge", "ops-runbook"));
        Document memoryByType = new Document("memory-type", "memory", Map.of("memory_type", "ops_chat"));
        Document memoryByKnowledge = new Document("memory-knowledge", "memory", Map.of("knowledge", "ops-chat-memory"));
        RagRecallCoordinator coordinator = new RagRecallCoordinator(
                new RagRecallCoordinator.RepositoryVectorRecall() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public List<Document> search(String query, String filterExpression, int topK) {
                        return List.of(knowledge, memoryByType, memoryByKnowledge);
                    }
                },
                null,
                null,
                null);

        List<RagRankedDocument> filtered = coordinator.recall(
                "query",
                Map.of(),
                plan("vector"),
                null,
                List.of("query"));
        List<RagRankedDocument> retained = coordinator.recall(
                "query",
                Map.of("qa_exclude_memory_documents", false),
                plan("vector"),
                null,
                List.of("query"));

        assertEquals(List.of("knowledge"), filtered.stream().map(result -> result.document().getId()).toList());
        assertEquals(List.of("knowledge", "memory-type", "memory-knowledge"),
                retained.stream().map(result -> result.document().getId()).toList());
        assertFalse(filtered.stream().anyMatch(result -> result.document().getId().startsWith("memory")));
        assertTrue(retained.stream().anyMatch(result -> result.document().getId().startsWith("memory")));
    }

    @Test
    void shouldIgnoreNullRecallListsWithoutBreakingOtherSources() {
        RagRecallCoordinator coordinator = new RagRecallCoordinator(
                new RagRecallCoordinator.RepositoryVectorRecall() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public List<Document> search(String query, String filterExpression, int topK) {
                        return null;
                    }
                },
                null,
                null,
                (query, filterExpression, topK) -> List.of(document("lexical")));

        List<RagRankedDocument> results = coordinator.recall(
                "query",
                Map.of("qa_exclude_memory_documents", false),
                plan("hybrid"),
                null,
                List.of("query"));

        assertEquals(1, results.size());
        assertEquals("lexical", results.get(0).document().getId());
    }

    private RagRetrievalPlan plan(String mode) {
        return new RagRetrievalPlan(
                mode,
                3,
                4,
                5,
                12000,
                false,
                "none",
                null,
                null,
                null,
                5,
                5,
                1200);
    }

    private static Document document(String id) {
        return new Document(id, id + " text", Map.of("source", "test"));
    }
}
