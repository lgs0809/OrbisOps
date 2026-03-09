package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionConfiguration;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySelectionPolicyTest {

    private final MemorySelectionPolicy policy = new MemorySelectionPolicy();

    @Test
    void filtersInactiveAndSupersededCandidates() {
        MemorySelectionResult result = policy.select(
                List.of(
                        item("active", 1, Map.of("memory_status", "ACTIVE")),
                        item("deleted", 2, Map.of("memory_status", "DELETED")),
                        item("disabled", 3, Map.of("status", "disabled")),
                        item("superseded", 4, Map.of("superseded_by", "new-memory")),
                        item(" ", 5, Map.of())),
                List.of(
                        message("active-message", "assistant", 1, Map.of()),
                        message("deleted-message", "assistant", 2, Map.of("status", "DELETED"))),
                new MemorySelectionConfiguration(true, 6D));

        assertEquals(List.of("active"), result.items().stream().map(MemoryItemCandidate::content).toList());
        assertEquals(List.of("active-message"), result.messages().stream().map(MemoryMessageCandidate::content).toList());
    }

    @Test
    void duplicateWinnerUsesNewestTurnIndex() {
        MemoryItemCandidate olderItem = item("same", 1, Map.of("turn_index", 1));
        MemoryItemCandidate newerItem = item("same", 1, Map.of("turn_index", 7));
        MemoryMessageCandidate olderMessage = message("same-message", "user", 2, Map.of("turn_index", 2));
        MemoryMessageCandidate newerMessage = message("same-message", "user", 8, Map.of("turnIndex", 8));

        MemorySelectionResult result = policy.select(
                List.of(olderItem, newerItem),
                List.of(olderMessage, newerMessage),
                new MemorySelectionConfiguration(true, 6D));

        assertEquals(1, result.items().size());
        assertEquals(7, result.items().get(0).metadata().get("turn_index"));
        assertEquals(1, result.messages().size());
        assertEquals(8, result.messages().get(0).metadata().get("turnIndex"));
    }

    @Test
    void recencyAndImportanceDetermineStableOrdering() {
        MemoryItemCandidate oldImportant = new MemoryItemCandidate(
                "s1", "u1", "fact", "old-important", BigDecimal.valueOf(1.5), "[]", "user", "h1",
                Map.of("turn_index", 1), "old");
        MemoryItemCandidate newNormal = new MemoryItemCandidate(
                "s1", "u1", "fact", "new-normal", BigDecimal.ONE, "[]", "user", "h2",
                Map.of("turn_index", 10), "new");
        MemoryMessageCandidate assistant = message("assistant-message", "assistant", 10, Map.of("turn_index", 10));
        MemoryMessageCandidate user = message("user-message", "user", 10, Map.of("turn_index", 10));

        MemorySelectionResult result = policy.select(
                List.of(oldImportant, newNormal),
                List.of(assistant, user),
                new MemorySelectionConfiguration(true, 6D));

        assertEquals("new-normal", result.items().get(0).content());
        assertEquals("user-message", result.messages().get(0).content());
    }

    @Test
    void disabledRecencyKeepsImportanceDominant() {
        MemorySelectionResult result = policy.select(
                List.of(
                        new MemoryItemCandidate(
                                "s1", "u1", "fact", "old-important", BigDecimal.valueOf(1.4), "[]", "user", "h1",
                                Map.of("turn_index", 1), "old"),
                        new MemoryItemCandidate(
                                "s1", "u1", "fact", "new-normal", BigDecimal.ONE, "[]", "user", "h2",
                                Map.of("turn_index", 100), "new")),
                List.of(),
                new MemorySelectionConfiguration(false, 6D));

        assertEquals("old-important", result.items().get(0).content());
        assertTrue(result.messages().isEmpty());
    }

    private MemoryItemCandidate item(String content, int turn, Map<String, Object> extraMetadata) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>(extraMetadata);
        metadata.putIfAbsent("turn_index", turn);
        return new MemoryItemCandidate(
                "s1", "u1", "fact", content, BigDecimal.ONE, "[]", "user", "hash", metadata, "");
    }

    private MemoryMessageCandidate message(String content,
                                           String role,
                                           int turn,
                                           Map<String, Object> extraMetadata) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>(extraMetadata);
        metadata.putIfAbsent("turn_index", turn);
        return new MemoryMessageCandidate("s1", "u1", role, content, "", metadata);
    }
}
