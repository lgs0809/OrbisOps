package cn.lgs.orbisops.domain.memory.model;

import java.math.BigDecimal;

/** Raw deterministic or model-produced draft normalized by the memory extraction domain policy. */
public record MemoryExtractionDraft(
        String memoryType,
        String content,
        BigDecimal importance,
        String tagsJson,
        String source,
        String scopeType,
        String title,
        String summary,
        String reason) {
}
