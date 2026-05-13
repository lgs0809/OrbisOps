package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryFusionPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryRetrievalApplicationServiceTest {

    @Test
    void recallsVectorThenLexicalWithCalculatedLimitAndFusesResults() {
        List<String> calls = new ArrayList<>();
        AtomicInteger vectorLimit = new AtomicInteger();
        AtomicInteger lexicalLimit = new AtomicInteger();
        SemanticVectorRecallPort vectorPort = (sessionId, userId, query, recallLimit) -> {
            calls.add("vector");
            vectorLimit.set(recallLimit);
            return List.of(candidate("doc-1", "vector", 1, 0.2D));
        };
        SemanticLexicalRecallPort lexicalPort = (sessionId, userId, query, recallLimit) -> {
            calls.add("lexical");
            lexicalLimit.set(recallLimit);
            return List.of(candidate("doc-1", "lexical", 2, 0.1D));
        };
        SemanticMemoryRetrievalApplicationService service = service(
                vectorPort, lexicalPort, null, fusionPolicy());

        List<SemanticMemoryDocumentSnapshot> result = service.search(query(true, 2, 8));

        assertEquals(List.of("vector", "lexical"), calls);
        assertEquals(32, vectorLimit.get());
        assertEquals(32, lexicalLimit.get());
        assertEquals(1, result.size());
        assertEquals(0.3D, (Double) result.get(0).metadata().get("memory_rrf_score"), 0.0000001D);
        assertEquals("vector,lexical", result.get(0).metadata().get("memory_retrieval_sources"));
    }

    @Test
    void embeddingUnavailableSkipsVectorAndStillUsesLexicalRecall() {
        AtomicInteger vectorCalls = new AtomicInteger();
        AtomicInteger lexicalCalls = new AtomicInteger();
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) -> {
                    vectorCalls.incrementAndGet();
                    return List.of();
                },
                (sessionId, userId, query, recallLimit) -> {
                    lexicalCalls.incrementAndGet();
                    return List.of(candidate("doc-1", "lexical", 1, 0.2D));
                },
                null,
                fusionPolicy());

        List<SemanticMemoryDocumentSnapshot> result = service.search(query(false, 8, 8));

        assertEquals(0, vectorCalls.get());
        assertEquals(1, lexicalCalls.get());
        assertEquals(1, result.size());
    }

    @Test
    void vectorFailureIsObservedWithoutBlockingLexicalRecall() {
        List<String> failures = new ArrayList<>();
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) -> {
                    throw new IllegalStateException("vector down");
                },
                (sessionId, userId, query, recallLimit) ->
                        List.of(candidate("doc-1", "lexical", 1, 0.2D)),
                (operation, error) -> failures.add(operation + ":" + error.getMessage()),
                fusionPolicy());

        List<SemanticMemoryDocumentSnapshot> result = service.search(query(true, 8, 8));

        assertEquals(List.of("vector-recall:vector down"), failures);
        assertEquals(1, result.size());
    }

    @Test
    void lexicalFailureIsObservedWithoutDiscardingVectorRecall() {
        List<String> failures = new ArrayList<>();
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) ->
                        List.of(candidate("doc-1", "vector", 1, 0.2D)),
                (sessionId, userId, query, recallLimit) -> {
                    throw new IllegalStateException("fts down");
                },
                (operation, error) -> failures.add(operation + ":" + error.getMessage()),
                fusionPolicy());

        List<SemanticMemoryDocumentSnapshot> result = service.search(query(true, 8, 8));

        assertEquals(List.of("lexical-recall:fts down"), failures);
        assertEquals(1, result.size());
    }

    @Test
    void failureObserverExceptionCannotBlockFallback() {
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) -> {
                    throw new IllegalStateException("vector down");
                },
                (sessionId, userId, query, recallLimit) ->
                        List.of(candidate("doc-1", "lexical", 1, 0.2D)),
                (operation, error) -> {
                    throw new IllegalStateException("observer down");
                },
                fusionPolicy());

        assertEquals(1, service.search(query(true, 8, 8)).size());
    }

    @Test
    void invalidQueryShortCircuitsAllPorts() {
        AtomicInteger calls = new AtomicInteger();
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) -> {
                    calls.incrementAndGet();
                    return List.of();
                },
                (sessionId, userId, query, recallLimit) -> {
                    calls.incrementAndGet();
                    return List.of();
                },
                null,
                fusionPolicy());

        assertEquals(List.of(), service.search(null));
        assertEquals(List.of(), service.search(new SemanticMemoryRetrievalQuery(
                "", "u1", "query", 8, 8, true, true, 6D)));
        assertEquals(List.of(), service.search(new SemanticMemoryRetrievalQuery(
                "s1", "u1", " ", 8, 8, true, true, 6D)));
        assertEquals(0, calls.get());
    }

    @Test
    void recallLimitPreservesHistoricalFloorMultiplierAndCap() {
        SemanticMemoryRetrievalApplicationService service = service(
                null, null, null, fusionPolicy());

        assertEquals(1, service.recallLimit(0, 0));
        assertEquals(32, service.recallLimit(2, 8));
        assertEquals(40, service.recallLimit(100, 8));
        assertEquals(40, service.recallLimit(8, 100));
    }

    @Test
    void nullPortResultsAreTreatedAsEmpty() {
        SemanticMemoryRetrievalApplicationService service = service(
                (sessionId, userId, query, recallLimit) -> null,
                (sessionId, userId, query, recallLimit) -> null,
                null,
                fusionPolicy());

        assertTrue(service.search(query(true, 8, 8)).isEmpty());
    }

    private SemanticMemoryRetrievalApplicationService service(
            SemanticVectorRecallPort vectorPort,
            SemanticLexicalRecallPort lexicalPort,
            SemanticMemoryRetrievalFailurePort failurePort,
            SemanticMemoryFusionPolicy fusionPolicy) {
        return new SemanticMemoryRetrievalApplicationService(
                vectorPort,
                lexicalPort,
                failurePort,
                fusionPolicy);
    }

    private SemanticMemoryFusionPolicy fusionPolicy() {
        return new SemanticMemoryFusionPolicy(
                new SemanticMemoryPolicy(new MemoryContentHashPolicy()));
    }

    private SemanticMemoryRetrievalQuery query(boolean embeddingAvailable,
                                               int limit,
                                               int topK) {
        return new SemanticMemoryRetrievalQuery(
                "session-1",
                "user-1",
                "join metric",
                limit,
                topK,
                embeddingAvailable,
                true,
                6D);
    }

    private SemanticMemoryRankedCandidate candidate(String id,
                                                     String source,
                                                     int rank,
                                                     double score) {
        return new SemanticMemoryRankedCandidate(
                new SemanticMemoryDocumentSnapshot(
                        id,
                        "content-" + id,
                        Map.of(
                                "turn_index", 1,
                                "importance", 0.5D,
                                "memory_kind", "message")),
                source,
                rank,
                score);
    }
}
