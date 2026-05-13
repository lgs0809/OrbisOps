package cn.lgs.orbisops.domain.memory.model;

import java.util.List;

/** Distinct active memory candidates ordered by domain relevance. */
public record MemorySelectionResult(
        List<MemoryItemCandidate> items,
        List<MemoryMessageCandidate> messages) {

    public MemorySelectionResult {
        items = items == null ? List.of() : List.copyOf(items);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static MemorySelectionResult empty() {
        return new MemorySelectionResult(List.of(), List.of());
    }
}
