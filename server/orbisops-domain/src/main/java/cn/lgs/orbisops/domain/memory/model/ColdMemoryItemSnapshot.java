package cn.lgs.orbisops.domain.memory.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable cold-store representation of a durable memory item. */
public record ColdMemoryItemSnapshot(
        String sessionId,
        String userId,
        String memoryType,
        String content,
        BigDecimal importance,
        String tagsJson,
        String sourceMessageRole,
        String sourceMessageHash,
        Map<String, Object> metadata,
        String createdAt) {

    public ColdMemoryItemSnapshot {
        importance = importance == null ? BigDecimal.valueOf(0.5D) : importance;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
