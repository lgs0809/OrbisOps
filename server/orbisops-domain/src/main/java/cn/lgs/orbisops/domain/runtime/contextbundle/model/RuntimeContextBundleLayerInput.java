package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record RuntimeContextBundleLayerInput(
        String bundleId,
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String actor,
        String query,
        String memoryContext,
        Map<String, Object> metadata,
        Instant createdAt) {

    public RuntimeContextBundleLayerInput {
        bundleId = required(bundleId, "RUNTIME_CONTEXT_BUNDLE_ID_REQUIRED");
        runId = text(runId);
        sessionId = text(sessionId);
        projectId = text(projectId);
        agentId = text(agentId);
        actor = text(actor);
        query = text(query);
        memoryContext = text(memoryContext);
        metadata = immutableMap(metadata);
        if (createdAt == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_TIME_REQUIRED");
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
