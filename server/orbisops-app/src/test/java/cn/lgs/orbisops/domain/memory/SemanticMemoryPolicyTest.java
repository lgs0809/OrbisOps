package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryPolicyTest {

    private final SemanticMemoryPolicy policy = new SemanticMemoryPolicy(new MemoryContentHashPolicy());

    @Test
    void baseMetadataPreservesHistoricalSemanticNamespace() {
        Map<String, Object> metadata = policy.baseMetadata(" session-1 ", " user-1 ", " message ");

        assertEquals("ops_chat", metadata.get("memory_type"));
        assertEquals("message", metadata.get("memory_kind"));
        assertEquals("ops-chat-memory", metadata.get("knowledge"));
        assertEquals("session-1", metadata.get("session_id"));
        assertEquals("user-1", metadata.get("user_id"));
        assertEquals("ops_memory_facade", metadata.get("source"));
    }

    @Test
    void activeRejectsSupersededAndDisabledLifecycleStates() {
        assertTrue(policy.active(null));
        assertTrue(policy.active(Map.of()));
        assertTrue(policy.active(Map.of("memory_status", "ACTIVE")));
        assertTrue(policy.active(Map.of("status", "UNKNOWN")));
        assertFalse(policy.active(Map.of("superseded_by", "memory-2")));
        assertFalse(policy.active(Map.of("memory_status", " superseded ")));
        assertFalse(policy.active(Map.of("memory_status", "DELETED")));
        assertFalse(policy.active(Map.of("status", "disabled")));
    }

    @Test
    void scopeRequiresNamespaceKindSessionAndCompatibleUser() {
        Map<String, Object> metadata = new LinkedHashMap<>(policy.baseMetadata(
                "session-1", "user-1", "message"));
        metadata.put("memory_status", "ACTIVE");

        assertTrue(policy.inScope(metadata, "session-1", "user-1"));
        assertTrue(policy.inScope(metadata, "session-1", ""));
        assertFalse(policy.inScope(metadata, "session-2", "user-1"));
        assertFalse(policy.inScope(metadata, "session-1", "user-2"));
        assertFalse(policy.inScope(new LinkedHashMap<>(Map.of(
                "memory_type", "other",
                "memory_kind", "message",
                "session_id", "session-1")), "session-1", ""));

        metadata.put("user_id", "");
        assertTrue(policy.inScope(metadata, "session-1", "user-2"));
        metadata.put("superseded_by", "newer");
        assertFalse(policy.inScope(metadata, "session-1", "user-2"));
    }

    @Test
    void rrfScoreUsesHistoricalConstantAndMinimumRank() {
        assertEquals(1.0D / 61D, policy.rrfScore(1), 0.0000001D);
        assertEquals(1.0D / 61D, policy.rrfScore(0), 0.0000001D);
        assertEquals(1.0D / 70D, policy.rrfScore(10), 0.0000001D);
    }

    @Test
    void scoreCombinesRrfRecencyImportanceAndPreferenceWeight() {
        Map<String, Object> metadata = Map.of(
                "memory_rrf_score", 0.8D,
                "turn_index", 10,
                "importance", 1.0D,
                "memory_kind", "preference");

        double score = policy.score(metadata, 16, true, 6D);

        assertEquals(0.54D, score, 0.0000001D);
    }

    @Test
    void scoreConvertsDistanceAndUsesMissingTurnAndSummaryWeights() {
        Map<String, Object> metadata = Map.of(
                "distance", 0.2D,
                "memory_kind", "summary");

        double score = policy.score(metadata, 20, true, 6D);

        assertEquals(0.646D, score, 0.0000001D);
    }

    @Test
    void scoreFallsBackForInvalidValuesAndCanDisableRecency() {
        Map<String, Object> metadata = Map.of(
                "score", "invalid",
                "importance", "invalid",
                "turn_index", "invalid",
                "memory_kind", "message");

        assertEquals(0.5D, policy.score(metadata, 100, false, 6D), 0.0000001D);
        assertEquals(BigDecimal.valueOf(0.5D), policy.importance(null));
        assertEquals(BigDecimal.valueOf(0.5D), policy.importance("invalid"));
        assertEquals(0L, policy.turnIndex(metadata));
    }

    @Test
    void recencyUsesCurrentOrFutureTurnAsFullWeightAndClampsHalfLife() {
        Map<String, Object> current = Map.of(
                "score", 1D,
                "importance", 0.5D,
                "turn_index", 10,
                "memory_kind", "message");
        Map<String, Object> older = Map.of(
                "score", 1D,
                "importance", 0.5D,
                "turn_index", 9,
                "memory_kind", "message");

        assertEquals(1D, policy.score(current, 10, true, 0D), 0.0000001D);
        assertEquals(0.5D, policy.score(older, 10, true, 0D), 0.0000001D);
    }

    @Test
    void documentKeyUsesIdThenSourceHashThenStableContentHash() {
        assertEquals("doc-1", policy.documentKey(
                "doc-1", "content", Map.of("source_message_hash", "source-hash")));
        assertEquals("source-hash", policy.documentKey(
                "", "content", Map.of("source_message_hash", "source-hash")));
        String contentKey = policy.documentKey("", "content", Map.of());
        assertEquals(contentKey, policy.documentKey(null, "content", null));
        assertNotEquals(contentKey, policy.documentKey("", "different", Map.of()));
        assertFalse(contentKey.isBlank());
    }
}
