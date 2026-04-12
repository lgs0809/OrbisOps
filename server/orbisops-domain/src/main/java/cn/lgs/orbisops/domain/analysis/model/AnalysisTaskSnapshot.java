package cn.lgs.orbisops.domain.analysis.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AnalysisTaskSnapshot(
        String runId,
        String projectId,
        String sessionId,
        String userId,
        String agentId,
        int agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        String storedStatus,
        Map<String, Object> request,
        Map<String, Object> response,
        String storedError,
        Instant createdAt,
        Instant updatedAt) {

    public AnalysisTaskSnapshot {
        runId = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        projectId = required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED");
        sessionId = text(sessionId);
        userId = text(userId);
        agentId = text(agentId);
        agentVersion = Math.max(0, agentVersion);
        agentDefinitionHash = text(agentDefinitionHash);
        executionHarness = text(executionHarness);
        storedStatus = text(storedStatus).toUpperCase(java.util.Locale.ROOT);
        request = immutable(request);
        response = immutable(response);
        storedError = text(storedError);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
