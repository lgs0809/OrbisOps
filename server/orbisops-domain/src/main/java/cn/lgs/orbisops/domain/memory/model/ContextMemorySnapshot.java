package cn.lgs.orbisops.domain.memory.model;

import java.math.BigDecimal;

/** Immutable persisted representation of one user/project context memory. */
public record ContextMemorySnapshot(
        Long id,
        String memoryId,
        String scopeType,
        String scopeId,
        String memoryType,
        String title,
        String summary,
        String content,
        String keywords,
        String status,
        BigDecimal confidence,
        String sourceType,
        String sourceId,
        String sourceMessageHash,
        String createdBy,
        String createTime,
        String updateTime,
        String expireTime) {

    public ContextMemorySnapshot {
        memoryId = value(memoryId);
        scopeType = value(scopeType);
        scopeId = value(scopeId);
        memoryType = value(memoryType);
        title = value(title);
        summary = value(summary);
        content = value(content);
        keywords = value(keywords);
        status = value(status);
        confidence = confidence == null ? BigDecimal.valueOf(0.8D) : confidence;
        sourceType = value(sourceType);
        sourceId = value(sourceId);
        sourceMessageHash = value(sourceMessageHash);
        createdBy = value(createdBy);
        createTime = value(createTime);
        updateTime = value(updateTime);
        expireTime = value(expireTime);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
