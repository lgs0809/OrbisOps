package cn.lgs.orbisops.domain.chatsession.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable persistence snapshot for a chat session read/write boundary. */
public record ChatSessionSnapshot(
        String sessionId,
        String userId,
        String projectId,
        String agentId,
        String agentBindingMode,
        Integer agentVersion,
        String agentDefinitionHash,
        String title,
        String mode,
        String engine,
        boolean ragEnabled,
        String knowledgeBaseId,
        String status,
        long stateVersion,
        Map<String, Object> metadata,
        String createdAt,
        String lastActiveAt,
        int messageCount,
        String lastMessage) {

    public ChatSessionSnapshot {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public boolean favorite() {
        return Boolean.TRUE.equals(metadata.get("favorite"));
    }
}
