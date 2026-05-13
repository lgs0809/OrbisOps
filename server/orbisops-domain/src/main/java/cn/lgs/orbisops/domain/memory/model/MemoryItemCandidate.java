package cn.lgs.orbisops.domain.memory.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable durable-memory candidate evaluated by the domain selection policy. */
public record MemoryItemCandidate(
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

    public MemoryItemCandidate {
        importance = importance == null ? BigDecimal.valueOf(0.5D) : importance;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String dedupKey() {
        return value(memoryType) + ":" + value(content);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
