package cn.lgs.orbisops.domain.memory.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed persisted projection of one governed explicit-memory version pointer. */
public record GovernedMemorySnapshot(
        long databaseId,
        String memoryId,
        MemoryScope scope,
        String scopeId,
        String userId,
        String projectId,
        String agentId,
        String sessionId,
        MemoryType type,
        String logicalKey,
        String content,
        String normalizedContent,
        String sourceType,
        String sourceRunId,
        boolean verified,
        double confidence,
        String riskLevel,
        String status,
        int version,
        String memoryHash,
        List<Map<String, Object>> proofRefs,
        Instant expiresAt,
        String createdBy,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public GovernedMemorySnapshot {
        memoryId = value(memoryId);
        if (scope == null) throw new IllegalArgumentException("MEMORY_SCOPE_REQUIRED");
        scopeId = value(scopeId);
        userId = value(userId);
        projectId = value(projectId);
        agentId = value(agentId);
        sessionId = value(sessionId);
        if (type == null || !type.persistable()) throw new IllegalArgumentException("MEMORY_TYPE_NOT_PERSISTABLE");
        logicalKey = value(logicalKey);
        content = value(content);
        normalizedContent = value(normalizedContent);
        sourceType = value(sourceType);
        sourceRunId = value(sourceRunId);
        riskLevel = value(riskLevel);
        status = value(status);
        memoryHash = value(memoryHash);
        proofRefs = immutableMaps(proofRefs);
        createdBy = value(createdBy);
        idempotencyKey = value(idempotencyKey);
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<Map<String, Object>> copy = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(value == null ? Map.of() : value)));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
