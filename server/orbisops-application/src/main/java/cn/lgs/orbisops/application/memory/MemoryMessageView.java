package cn.lgs.orbisops.application.memory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework-neutral message read model used by runtime memory retrieval. */
public record MemoryMessageView(
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata) {

    public MemoryMessageView {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
