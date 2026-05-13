package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMmrDiversitySelectorTest {

    private final RagMmrDiversitySelector selector = new RagMmrDiversitySelector();

    @Test
    void shouldPreferDiverseSecondDocumentAndAppendRemainingCandidates() {
        Document first = document(
                "first",
                "锁单失败 分布式锁",
                Map.of("retrieval_score", 1.0d));
        Document nearDuplicate = document(
                "near",
                "锁单失败 分布式锁 重试",
                Map.of("retrieval_score", 0.95d));
        Document diverse = document(
                "diverse",
                "数据库连接超时",
                Map.of("retrieval_score", 0.80d));
        List<Document> candidates = List.of(first, nearDuplicate, diverse);

        List<Document> selected = selector.select(
                "锁单失败",
                candidates,
                2,
                Map.of("qa_mmr_lambda", 0.5d));

        assertEquals(List.of("first", "diverse", "near"),
                selected.stream().map(Document::getId).toList());
        assertEquals(true, selected.get(0).getMetadata().get("mmr_selected"));
        assertEquals(true, selected.get(1).getMetadata().get("mmr_selected"));
        assertFalse(selected.get(2).getMetadata().containsKey("mmr_selected"));
        assertEquals(0.5d,
                ((Number) selected.get(0).getMetadata().get("mmr_score")).doubleValue(),
                0.000000000001d);
        assertEquals(0.4d,
                ((Number) selected.get(1).getMetadata().get("mmr_score")).doubleValue(),
                0.000000000001d);
        assertFalse(first.getMetadata().containsKey("mmr_score"));
        assertFalse(diverse.getMetadata().containsKey("mmr_selected"));
    }

    @Test
    void shouldUseRerankThenRetrievalThenBm25MetadataPriority() {
        Document rerankWinsOverHigherRetrieval = document(
                "rerank",
                "alpha",
                Map.of("rerank_score", "0.40", "retrieval_score", 0.99d));
        Document retrieval = document(
                "retrieval",
                "beta",
                Map.of("retrieval_score", 0.50d, "bm25_score", 0.90d));
        Document bm25 = document(
                "bm25",
                "gamma",
                Map.of("bm25_score", 0.30d));

        List<Document> selected = selector.select(
                "unmatched query",
                List.of(rerankWinsOverHigherRetrieval, retrieval, bm25),
                2,
                Map.of("qa_mmr_lambda", 0.95d));

        assertEquals("retrieval", selected.get(0).getId());
        assertEquals("rerank", selected.get(1).getId());
    }

    @Test
    void shouldContinueToLowerPriorityScoreWhenHigherPriorityValueIsMalformed() {
        Document malformed = document(
                "malformed",
                "alpha",
                Map.of("rerank_score", "not-a-number", "retrieval_score", 0.90d));
        Document lower = document(
                "lower",
                "beta",
                Map.of("retrieval_score", 0.50d));
        Document third = document(
                "third",
                "gamma",
                Map.of("retrieval_score", 0.10d));

        List<Document> selected = selector.select(
                "query",
                List.of(lower, third, malformed),
                2,
                Map.of("qa_mmr_lambda", 0.95d));

        assertEquals("malformed", selected.get(0).getId());
    }

    @Test
    void shouldFallbackToLexicalJaccardWhenScoreMetadataIsMissing() {
        Document irrelevant = document("irrelevant", "支付回调签名", Map.of());
        Document matching = document("matching", "database timeout troubleshooting", Map.of());
        Document third = document("third", "库存补偿任务", Map.of());

        List<Document> selected = selector.select(
                "database timeout",
                List.of(irrelevant, matching, third),
                2,
                Map.of("qa_mmr_lambda", 0.95d));

        assertEquals("matching", selected.get(0).getId());
    }

    @Test
    void shouldClampLambdaAndFallbackToDefaultForMalformedValue() {
        List<Document> candidates = List.of(
                document("first", "alpha", Map.of("retrieval_score", 1.0d)),
                document("second", "beta", Map.of("retrieval_score", 0.5d)),
                document("third", "gamma", Map.of("retrieval_score", 0.2d)));

        List<Document> upper = selector.select("query", candidates, 2, Map.of("qa_mmr_lambda", 2.0d));
        List<Document> lower = selector.select("query", candidates, 2, Map.of("qa_mmr_lambda", -1.0d));
        List<Document> malformed = selector.select("query", candidates, 2, Map.of("qa_mmr_lambda", "bad"));

        assertEquals(0.95d, score(upper.get(0)), 0.000000000001d);
        assertEquals(0.10d, score(lower.get(0)), 0.000000000001d);
        assertEquals(0.72d, score(malformed.get(0)), 0.000000000001d);
    }

    @Test
    void shouldReturnOriginalListWhenDisabledTooSmallOrTopKIsOne() {
        List<Document> three = List.of(
                document("a", "a text", Map.of()),
                document("b", "b text", Map.of()),
                document("c", "c text", Map.of()));
        List<Document> two = three.subList(0, 2);

        assertSame(three, selector.select("query", three, 2, Map.of("qa_mmr_enabled", false)));
        assertSame(two, selector.select("query", two, 2, Map.of()));
        assertSame(three, selector.select("query", three, 1, Map.of()));
    }

    @Test
    void shouldKeepFirstCandidateWhenScoresAreEqual() {
        List<Document> candidates = List.of(
                document("first", "alpha", Map.of("retrieval_score", 0.5d)),
                document("second", "beta", Map.of("retrieval_score", 0.5d)),
                document("third", "gamma", Map.of("retrieval_score", 0.5d)));

        List<Document> selected = selector.select(
                "query",
                candidates,
                2,
                Map.of("qa_mmr_lambda", 0.95d));

        assertEquals("first", selected.get(0).getId());
        assertTrue(selected.get(0).getMetadata().containsKey("mmr_score"));
    }

    private double score(Document document) {
        return ((Number) document.getMetadata().get("mmr_score")).doubleValue();
    }

    private Document document(String id, String text, Map<String, Object> metadata) {
        return new Document(id, text, metadata);
    }
}
