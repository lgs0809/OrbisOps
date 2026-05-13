package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionConfiguration;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Domain policy for active-state filtering, duplicate resolution and recency-aware relevance ordering.
 */
public class MemorySelectionPolicy {

    public MemorySelectionResult select(List<MemoryItemCandidate> itemCandidates,
                                        List<MemoryMessageCandidate> messageCandidates,
                                        MemorySelectionConfiguration configuration) {
        List<MemoryItemCandidate> safeItems = itemCandidates == null ? List.of() : itemCandidates;
        List<MemoryMessageCandidate> safeMessages = messageCandidates == null ? List.of() : messageCandidates;
        MemorySelectionConfiguration safeConfiguration = configuration == null
                ? new MemorySelectionConfiguration(true, 6D)
                : configuration;
        long currentTurn = Math.max(maxItemTurn(safeItems), maxMessageTurn(safeMessages));

        Map<String, MemoryItemCandidate> distinctItems = new LinkedHashMap<>();
        for (MemoryItemCandidate item : safeItems) {
            if (item == null || !hasText(item.content()) || !isActive(item.metadata())) continue;
            distinctItems.merge(item.dedupKey(), item, this::preferNewerItem);
        }
        List<MemoryItemCandidate> selectedItems = new ArrayList<>(distinctItems.values());
        selectedItems.sort(Comparator
                .comparingDouble((MemoryItemCandidate item) -> itemScore(item, currentTurn, safeConfiguration))
                .reversed());

        Map<String, MemoryMessageCandidate> distinctMessages = new LinkedHashMap<>();
        for (MemoryMessageCandidate message : safeMessages) {
            if (message == null || !hasText(message.content()) || !isActive(message.metadata())) continue;
            distinctMessages.merge(message.dedupKey(), message, this::preferNewerMessage);
        }
        List<MemoryMessageCandidate> selectedMessages = new ArrayList<>(distinctMessages.values());
        selectedMessages.sort(Comparator
                .comparingDouble((MemoryMessageCandidate message) -> messageScore(message, currentTurn, safeConfiguration))
                .reversed());

        return new MemorySelectionResult(selectedItems, selectedMessages);
    }

    private boolean isActive(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return true;
        Object supersededBy = metadata.get("superseded_by");
        if (supersededBy != null && hasText(String.valueOf(supersededBy))) return false;
        Object status = metadata.containsKey("memory_status")
                ? metadata.get("memory_status")
                : metadata.get("status");
        if (status == null) return true;
        String normalized = String.valueOf(status).trim().toUpperCase(Locale.ROOT);
        return !"SUPERSEDED".equals(normalized)
                && !"DELETED".equals(normalized)
                && !"DISABLED".equals(normalized);
    }

    private MemoryItemCandidate preferNewerItem(MemoryItemCandidate left, MemoryItemCandidate right) {
        return turnIndex(left.metadata()) >= turnIndex(right.metadata()) ? left : right;
    }

    private MemoryMessageCandidate preferNewerMessage(MemoryMessageCandidate left, MemoryMessageCandidate right) {
        if ("protected_source".equals(left.metadata().get("memory_type"))) return left;
        if ("protected_source".equals(right.metadata().get("memory_type"))) return right;
        return turnIndex(left.metadata()) >= turnIndex(right.metadata()) ? left : right;
    }

    private long maxItemTurn(List<MemoryItemCandidate> items) {
        return items.stream()
                .filter(item -> item != null)
                .map(MemoryItemCandidate::metadata)
                .mapToLong(this::turnIndex)
                .max()
                .orElse(0L);
    }

    private long maxMessageTurn(List<MemoryMessageCandidate> messages) {
        return messages.stream()
                .filter(message -> message != null)
                .map(MemoryMessageCandidate::metadata)
                .mapToLong(this::turnIndex)
                .max()
                .orElse(0L);
    }

    private double itemScore(MemoryItemCandidate item,
                             long currentTurn,
                             MemorySelectionConfiguration configuration) {
        double importance = item.importance() == null ? 0.5D : item.importance().doubleValue();
        double boundedImportance = Math.max(0.1D, Math.min(1.5D, importance));
        return recencyWeight(turnIndex(item.metadata()), currentTurn, configuration) * boundedImportance;
    }

    private double messageScore(MemoryMessageCandidate message,
                                long currentTurn,
                                MemorySelectionConfiguration configuration) {
        double roleWeight = "user".equalsIgnoreCase(value(message.role())) ? 1.08D : 1.0D;
        return recencyWeight(turnIndex(message.metadata()), currentTurn, configuration) * roleWeight;
    }

    private double recencyWeight(long turnIndex,
                                 long currentTurn,
                                 MemorySelectionConfiguration configuration) {
        if (!configuration.recencyAware() || turnIndex <= 0L || currentTurn <= 0L) return 1.0D;
        double age = Math.max(0D, currentTurn - turnIndex);
        return 1.0D / (1.0D + age / configuration.recencyHalfLifeTurns());
    }

    private long turnIndex(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return 0L;
        Object raw = metadata.containsKey("turn_index")
                ? metadata.get("turn_index")
                : metadata.get("turnIndex");
        if (raw == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
