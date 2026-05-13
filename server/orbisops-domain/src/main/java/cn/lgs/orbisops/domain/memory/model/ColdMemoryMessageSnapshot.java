package cn.lgs.orbisops.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable cold-store representation of a conversation message. */
public record ColdMemoryMessageSnapshot(
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata) {

    public ColdMemoryMessageSnapshot {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
