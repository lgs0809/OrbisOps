package cn.lgs.orbisops.application.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed semantic-memory message write request. */
public record SemanticMemoryWriteCommand(
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata,
        boolean embeddingAvailable) {

    public SemanticMemoryWriteCommand {
        sessionId = value(sessionId);
        userId = value(userId);
        role = value(role);
        content = content == null ? "" : content;
        createdAt = value(createdAt);
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
