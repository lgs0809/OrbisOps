package cn.lgs.orbisops.application.rag;

import java.time.LocalDateTime;

/** Immutable application model for one knowledge-base catalog entry. */
public record RagOrderDefinition(
        Long id,
        String ragId,
        String ragName,
        String knowledgeTag,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public RagOrderDefinition createdAt(LocalDateTime now) {
        return new RagOrderDefinition(
                id, ragId, ragName, knowledgeTag, status, now, now);
    }

    public RagOrderDefinition updatedAt(LocalDateTime now) {
        return new RagOrderDefinition(
                id, ragId, ragName, knowledgeTag, status, createTime, now);
    }
}
