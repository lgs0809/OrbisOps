package cn.lgs.orbisops.domain.chatsession.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable chat message read model owned by the Chat Session repository boundary. */
public record ChatMessageSnapshot(
        String messageId,
        String sessionId,
        String userId,
        String role,
        String content,
        String createdAt,
        Map<String, Object> metadata) {

    public ChatMessageSnapshot {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
