package cn.lgs.orbisops.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Domain result produced after capture defaults and invariants are applied. */
public record CapturedMemoryMessage(
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata) {

    public CapturedMemoryMessage {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
