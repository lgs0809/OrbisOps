package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;

import java.util.List;

/** Immutable result containing independently retrieved memory slices. */
public record MemoryRetrievalResult(
        List<ColdMemoryItemSnapshot> coldItems,
        List<MemoryMessageView> hotMessages,
        List<MemoryMessageView> semanticMessages,
        List<ContextMemoryView> contextMemories) {

    public MemoryRetrievalResult {
        coldItems = coldItems == null ? List.of() : List.copyOf(coldItems);
        hotMessages = hotMessages == null ? List.of() : List.copyOf(hotMessages);
        semanticMessages = semanticMessages == null ? List.of() : List.copyOf(semanticMessages);
        contextMemories = contextMemories == null ? List.of() : List.copyOf(contextMemories);
    }

    public static MemoryRetrievalResult empty() {
        return new MemoryRetrievalResult(List.of(), List.of(), List.of(), List.of());
    }
}
