package cn.lgs.orbisops.domain.memory.model;

import java.util.List;

/** Hot replacement and cold summary item produced by context compression. */
public record MemoryCompressionProjection(
        List<ColdMemoryMessageSnapshot> replacementMessages,
        ColdMemoryItemSnapshot summaryItem) {

    public MemoryCompressionProjection {
        replacementMessages = replacementMessages == null ? List.of() : List.copyOf(replacementMessages);
    }
}
