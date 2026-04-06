package cn.lgs.orbisops.domain.worksession.run.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkSessionRunStart(
        String runId,
        String projectId,
        String sessionId,
        String actor,
        String agentId,
        int agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        Map<String, Object> manifest,
        String manifestHash,
        Map<String, Object> requestPayload,
        String attemptId,
        String leaseToken,
        String workerId,
        Instant startedAt,
        Instant leaseExpiresAt) {

    public WorkSessionRunStart {
        runId = required(runId, "WORK_SESSION_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORK_SESSION_PROJECT_ID_REQUIRED");
        sessionId = required(sessionId, "WORK_SESSION_SESSION_ID_REQUIRED");
        actor = required(actor, "WORK_SESSION_OWNER_REQUIRED");
        agentId = required(agentId, "WORK_SESSION_AGENT_ID_REQUIRED");
        if (agentVersion <= 0) throw new IllegalArgumentException("WORK_SESSION_AGENT_VERSION_NOT_PINNED");
        agentDefinitionHash = required(agentDefinitionHash, "WORK_SESSION_AGENT_VERSION_NOT_PINNED");
        executionHarness = required(executionHarness, "WORK_SESSION_HARNESS_INVALID");
        manifest = immutable(manifest);
        manifestHash = required(manifestHash, "WORK_SESSION_MANIFEST_HASH_REQUIRED");
        requestPayload = immutable(requestPayload);
        attemptId = required(attemptId, "WORK_SESSION_ATTEMPT_ID_REQUIRED");
        leaseToken = required(leaseToken, "WORK_SESSION_LEASE_TOKEN_REQUIRED");
        workerId = required(workerId, "WORK_SESSION_WORKER_ID_REQUIRED");
        if (startedAt == null || leaseExpiresAt == null) {
            throw new IllegalArgumentException("WORK_SESSION_START_TIME_REQUIRED");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
