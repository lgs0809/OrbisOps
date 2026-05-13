package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryFusionPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryFusionPolicyTest {

    private final SemanticMemoryPolicy semanticPolicy = new SemanticMemoryPolicy(
            new MemoryContentHashPolicy());
    private final SemanticMemoryFusionPolicy fusionPolicy = new SemanticMemoryFusionPolicy(
            semanticPolicy);

    @Test
    void fusesVectorAndLexicalCandidatesByStableKeyAndPreservesSourceRanks() {
        SemanticMemoryDocumentSnapshot vectorDocument = document(
                "doc-1", "memory one", 2, 0.5D, "message", Map.of("memory_vector_score", 0.9D));
        SemanticMemoryDocumentSnapshot lexicalDocument = document(
                "doc-1", "memory one", 2, 0.5D, "message", Map.of("memory_lexical_score", 0.8D));
        SemanticMemoryDocumentSnapshot secondDocument = document(
                "doc-2", "memory two", 3, 0.5D, "message", Map.of());

        List<SemanticMemoryDocumentSnapshot> result = fusionPolicy.fuseAndRank(List.of(
                candidate(vectorDocument, "vector", 1, 0.20D),
                candidate(lexicalDocument, "lexical", 2, 0.10D),
                candidate(secondDocument, "vector", 3, 0.20D)), 8, false, 6D);

        assertEquals(2, result.size());
        SemanticMemoryDocumentSnapshot fused = result.get(0);
        assertEquals("doc-1", fused.id());
        assertEquals("memory one", fused.content());
        assertEquals(0.30D, (Double) fused.metadata().get("memory_rrf_score"), 0.0000001D);
        assertEquals("vector,lexical", fused.metadata().get("memory_retrieval_sources"));
        assertEquals(1, fused.metadata().get("memory_vector_rank"));
        assertEquals(2, fused.metadata().get("memory_lexical_rank"));
        assertEquals(0.9D, (Double) fused.metadata().get("memory_vector_score"), 0.0000001D);
        assertFalse(fused.metadata().containsKey("memory_lexical_score"));
    }

    @Test
    void groupingUsesSourceHashWhenDocumentIdIsMissing() {
        SemanticMemoryDocumentSnapshot first = document(
                "", "first payload", 1, 0.5D, "message", Map.of("source_message_hash", "same-hash"));
        SemanticMemoryDocumentSnapshot second = document(
                "", "second payload", 1, 0.5D, "message", Map.of("source_message_hash", "same-hash"));

        List<SemanticMemoryDocumentSnapshot> result = fusionPolicy.fuseAndRank(List.of(
                candidate(first, "vector", 1, 0.1D),
                candidate(second, "lexical", 1, 0.1D)), 8, false, 6D);

        assertEquals(1, result.size());
        assertEquals("first payload", result.get(0).content());
        assertEquals("vector,lexical", result.get(0).metadata().get("memory_retrieval_sources"));
    }

    @Test
    void finalRankingUsesCurrentTurnAcrossFusedDocuments() {
        SemanticMemoryDocumentSnapshot oldRelevant = document(
                "old", "old", 1, 0.5D, "message", Map.of());
        SemanticMemoryDocumentSnapshot recent = document(
                "recent", "recent", 10, 0.5D, "message", Map.of());

        List<SemanticMemoryDocumentSnapshot> result = fusionPolicy.fuseAndRank(List.of(
                candidate(oldRelevant, "vector", 1, 0.8D),
                candidate(recent, "lexical", 1, 0.6D)), 8, true, 6D);

        assertEquals(List.of("recent", "old"), result.stream()
                .map(SemanticMemoryDocumentSnapshot::id)
                .toList());
    }

    @Test
    void limitUsesHistoricalMinimumOneAndEmptyInputReturnsEmpty() {
        List<SemanticMemoryRankedCandidate> candidates = List.of(
                candidate(document("one", "one", 1, 0.5D, "message", Map.of()), "vector", 1, 0.3D),
                candidate(document("two", "two", 1, 0.5D, "message", Map.of()), "vector", 2, 0.2D));

        assertEquals(1, fusionPolicy.fuseAndRank(candidates, 0, false, 6D).size());
        assertEquals(List.of(), fusionPolicy.fuseAndRank(null, 8, false, 6D));
        assertEquals(List.of(), fusionPolicy.fuseAndRank(List.of(), 8, false, 6D));
    }

    @Test
    void snapshotsAndCandidatesAreImmutableAndNormalizeRank() {
        Map<String, Object> mutableMetadata = new LinkedHashMap<>();
        mutableMetadata.put("turn_index", 1);
        SemanticMemoryDocumentSnapshot snapshot = new SemanticMemoryDocumentSnapshot(
                null, null, mutableMetadata);
        mutableMetadata.put("forged", true);
        SemanticMemoryRankedCandidate candidate = new SemanticMemoryRankedCandidate(
                snapshot, " vector ", 0, 0.1D);

        assertEquals("", snapshot.id());
        assertEquals("", snapshot.content());
        assertFalse(snapshot.metadata().containsKey("forged"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.metadata().put("forged", true));
        assertEquals("vector", candidate.source());
        assertEquals(1, candidate.rank());
    }

    private SemanticMemoryRankedCandidate candidate(SemanticMemoryDocumentSnapshot document,
                                                     String source,
                                                     int rank,
                                                     double score) {
        return new SemanticMemoryRankedCandidate(document, source, rank, score);
    }

    private SemanticMemoryDocumentSnapshot document(String id,
                                                     String content,
                                                     long turnIndex,
                                                     double importance,
                                                     String kind,
                                                     Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(extraMetadata);
        metadata.put("turn_index", turnIndex);
        metadata.put("importance", importance);
        metadata.put("memory_kind", kind);
        return new SemanticMemoryDocumentSnapshot(id, content, metadata);
    }
}
