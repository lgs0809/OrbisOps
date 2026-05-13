package cn.lgs.orbisops.application.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed use-case command for governed explicit-memory creation. */
public record GovernedMemoryCreateCommand(
        String scopeType,
        String scopeId,
        String memoryType,
        String content,
        String normalizedContent,
        String logicalKey,
        String userId,
        String projectId,
        String agentId,
        String sessionId,
        String sourceType,
        String sourceRunId,
        boolean verified,
        double confidence,
        String riskLevel,
        List<Map<String, Object>> proofRefs,
        String actor) {

    public GovernedMemoryCreateCommand {
        scopeType = value(scopeType);
        scopeId = value(scopeId);
        memoryType = value(memoryType);
        content = content == null ? "" : content;
        normalizedContent = value(normalizedContent);
        logicalKey = value(logicalKey);
        userId = value(userId);
        projectId = value(projectId);
        agentId = value(agentId);
        sessionId = value(sessionId);
        sourceType = value(sourceType);
        sourceRunId = value(sourceRunId);
        riskLevel = value(riskLevel);
        proofRefs = immutableMaps(proofRefs);
        actor = value(actor);
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
