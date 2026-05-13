package cn.lgs.orbisops.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable input to the domain policy that prepares a captured conversation message. */
public record MemoryCaptureDraft(
        String sessionId,
        String userId,
        String role,
        String content,
        Map<String, Object> metadata) {

    public MemoryCaptureDraft {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public boolean valid() {
        return hasText(sessionId) && hasText(content);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
