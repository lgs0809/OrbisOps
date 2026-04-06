package cn.lgs.orbisops.domain.worksession.run.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkSessionRunSnapshot(
        String runId,
        String projectId,
        String sessionId,
        String owner,
        String agentId,
        int agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        WorkSessionRunStatus status,
        String currentAttemptId,
        long stateVersion,
        long fencingToken,
        String workerId,
        String leaseToken,
        Instant leaseExpiresAt,
        boolean cancelRequested,
        Map<String, Object> manifest,
        String manifestHash,
        Map<String, Object> requestPayload,
        Map<String, Object> responsePayload,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public WorkSessionRunSnapshot {
        runId = required(runId, "WORK_SESSION_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORK_SESSION_PROJECT_ID_REQUIRED");
        sessionId = text(sessionId);
        owner = text(owner);
        agentId = text(agentId);
        agentDefinitionHash = text(agentDefinitionHash);
        executionHarness = text(executionHarness);
        if (status == null) throw new IllegalArgumentException("WORK_SESSION_STATUS_REQUIRED");
        currentAttemptId = text(currentAttemptId);
        workerId = text(workerId);
        leaseToken = text(leaseToken);
        manifest = immutable(manifest);
        manifestHash = text(manifestHash);
        requestPayload = immutable(requestPayload);
        responsePayload = immutable(responsePayload);
        errorMessage = text(errorMessage);
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("WORK_SESSION_TIME_REQUIRED");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
