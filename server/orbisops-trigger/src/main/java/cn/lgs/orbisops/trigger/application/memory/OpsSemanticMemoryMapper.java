package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticMemoryRetrievalQuery;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteCommand;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;

import java.util.List;

/** Anti-corruption mapper between legacy semantic-memory models and typed application contracts. */
public class OpsSemanticMemoryMapper {

    public SemanticMemoryWriteCommand writeCommand(
            OpsMemoryMessage message,
            boolean embeddingAvailable) {
        if (message == null) {
            return null;
        }
        return new SemanticMemoryWriteCommand(
                message.getSessionId(),
                message.getUserId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt(),
                message.safeMetadata(),
                embeddingAvailable);
    }

    public SemanticMemoryRetrievalQuery retrievalQuery(
            String sessionId,
            String userId,
            String query,
            int limit,
            int semanticTopK,
            boolean embeddingAvailable,
            boolean recencyAware,
            double recencyHalfLifeTurns) {
        return new SemanticMemoryRetrievalQuery(
                sessionId,
                userId,
                query,
                limit,
                semanticTopK,
                embeddingAvailable,
                recencyAware,
                recencyHalfLifeTurns);
    }

    public List<OpsMemoryMessage> messageViews(
            List<SemanticMemoryDocumentSnapshot> documents,
            String sessionId,
            String userId) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .filter(document -> document != null)
                .filter(document -> "message".equals(String.valueOf(
                        document.metadata().get("memory_kind"))))
                .map(document -> OpsMemoryMessage.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .role(String.valueOf(document.metadata().getOrDefault("role", "assistant")))
                        .content(document.content())
                        .createdAt(String.valueOf(document.metadata().getOrDefault("created_at", "")))
                        .metadata(document.metadata())
                        .build())
                .toList();
    }
}
