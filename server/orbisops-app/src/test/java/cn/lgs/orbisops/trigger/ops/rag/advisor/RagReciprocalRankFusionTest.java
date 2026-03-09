package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RagReciprocalRankFusionTest {

    private final RagReciprocalRankFusion fusion = new RagReciprocalRankFusion();

    @Test
    void shouldMergeStableKeyScoresAndSourcesUsingFirstDocumentAsProjectionBase() {
        Document first = new Document(
                "shared",
                "first text",
                Map.of("source", "first.md", "first_only", true));
        Document second = new Document(
                "shared",
                "second text",
                Map.of("source", "second.md", "second_only", true));
        List<RagRankedDocument> ranked = List.of(
                new RagRankedDocument(first, "vector", 1, 0.4d),
                new RagRankedDocument(second, "vector", 2, 0.2d),
                new RagRankedDocument(second, "bm25", 1, 0.3d));

        List<Document> documents = fusion.fuse(ranked, plan(false, 5, 5));

        assertEquals(1, documents.size());
        Document fused = documents.get(0);
        assertEquals("shared", fused.getId());
        assertEquals("first text", fused.getText());
        assertEquals("first.md", fused.getMetadata().get("source"));
        assertEquals(true, fused.getMetadata().get("first_only"));
        assertFalse(fused.getMetadata().containsKey("second_only"));
        assertEquals("vector,bm25", fused.getMetadata().get("retrieval_sources"));
        assertEquals(0.9d,
                ((Number) fused.getMetadata().get("retrieval_score")).doubleValue(),
                0.000000000001d);
        assertFalse(first.getMetadata().containsKey("retrieval_sources"));
        assertFalse(second.getMetadata().containsKey("retrieval_score"));
    }

    @Test
    void shouldSortByFusedScoreAndUseFinalTopKWhenRerankIsDisabled() {
        List<RagRankedDocument> ranked = List.of(
                ranked("a", "vector", 0.20d),
                ranked("b", "vector", 0.50d),
                ranked("c", "bm25", 0.30d));

        List<Document> documents = fusion.fuse(ranked, plan(false, 2, 3));

        assertEquals(List.of("b", "c"), documents.stream().map(Document::getId).toList());
    }

    @Test
    void shouldUseRerankCandidateTopKWhenRerankIsEnabled() {
        List<RagRankedDocument> ranked = List.of(
                ranked("a", "vector", 0.50d),
                ranked("b", "vector", 0.40d),
                ranked("c", "bm25", 0.30d),
                ranked("d", "bm25", 0.20d));

        List<Document> documents = fusion.fuse(ranked, plan(true, 1, 3));

        assertEquals(List.of("a", "b", "c"), documents.stream().map(Document::getId).toList());
    }

    @Test
    void shouldKeepFirstSeenGroupOrderWhenFusedScoresAreEqual() {
        List<RagRankedDocument> ranked = List.of(
                ranked("first", "vector", 0.25d),
                ranked("second", "bm25", 0.25d),
                ranked("third", "multimodal", 0.25d));

        List<Document> documents = fusion.fuse(ranked, plan(false, 3, 3));

        assertEquals(List.of("first", "second", "third"),
                documents.stream().map(Document::getId).toList());
    }

    private RagRankedDocument ranked(String id, String source, double score) {
        return new RagRankedDocument(
                new Document(id, id + " text", Map.of("source", id + ".md")),
                source,
                1,
                score);
    }

    private RagRetrievalPlan plan(boolean rerankEnabled,
                                  int finalTopK,
                                  int rerankCandidateTopK) {
        return new RagRetrievalPlan(
                "hybrid",
                10,
                10,
                finalTopK,
                12000,
                rerankEnabled,
                rerankEnabled ? "cohere" : "none",
                null,
                null,
                null,
                rerankCandidateTopK,
                finalTopK,
                1200);
    }
}
