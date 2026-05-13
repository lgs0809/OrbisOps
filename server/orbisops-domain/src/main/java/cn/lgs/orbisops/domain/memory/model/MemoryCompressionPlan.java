package cn.lgs.orbisops.domain.memory.model;

import java.util.List;

/** Immutable partition of compressible historical messages and retained recent messages. */
public record MemoryCompressionPlan(
        List<ColdMemoryMessageSnapshot> olderMessages,
        List<ColdMemoryMessageSnapshot> recentMessages) {

    public MemoryCompressionPlan {
        olderMessages = olderMessages == null ? List.of() : List.copyOf(olderMessages);
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    public boolean required() {
        return !olderMessages.isEmpty();
    }

    public static MemoryCompressionPlan none() {
        return new MemoryCompressionPlan(List.of(), List.of());
    }
}
