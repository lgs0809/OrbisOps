package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;

import java.util.List;

/** Typed input for rendering an already-selected runtime memory context. */
public record MemoryContextRenderingRequest(
        List<ContextMemoryView> contextMemories,
        List<MemoryItemCandidate> items,
        List<MemoryMessageCandidate> messages,
        int contextMemoryLimit,
        int itemLimit,
        int messageLimit,
        int maxChars) {

    public MemoryContextRenderingRequest {
        contextMemories = contextMemories == null ? List.of() : List.copyOf(contextMemories);
        items = items == null ? List.of() : List.copyOf(items);
        messages = messages == null ? List.of() : List.copyOf(messages);
        contextMemoryLimit = Math.max(1, contextMemoryLimit);
        itemLimit = Math.max(1, itemLimit);
        messageLimit = Math.max(1, messageLimit);
        maxChars = Math.max(1200, maxChars);
    }
}
