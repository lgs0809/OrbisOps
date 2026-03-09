package cn.lgs.orbisops.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable conversation-message candidate evaluated by the domain selection policy. */
public record MemoryMessageCandidate(
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata) {

    public MemoryMessageCandidate {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String dedupKey() {
        return value(role) + ":" + value(content);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
