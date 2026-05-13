package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

/** Typed input for long-term memory extraction orchestration. */
public record MemoryExtractionCommand(
        ColdMemoryMessageSnapshot message,
        int maxItems,
        boolean modelEnabled,
        int modelMaxInputChars) {
}
