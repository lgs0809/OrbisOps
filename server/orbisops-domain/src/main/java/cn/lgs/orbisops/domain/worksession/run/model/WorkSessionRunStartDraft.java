package cn.lgs.orbisops.domain.worksession.run.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkSessionRunStartDraft(
        String runId,
        String projectId,
        String sessionId,
        String actor,
        String agentId,
        Integer agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        int harnessVersion,
        String harnessHash,
        String engine,
        String adapterKey,
        String modelId,
        String modelProfileId,
        long modelProfileVersion,
        Map<String, Object> promptIdentity,
        Map<String, Object> requestIdentity,
        Map<String, Object> requestPayload,
        Map<String, Object> metadata,
        String attemptId,
        String leaseToken,
        String workerId,
        Instant startedAt,
        Instant leaseExpiresAt) {

    public WorkSessionRunStartDraft {
        runId = text(runId);
        projectId = text(projectId);
        sessionId = text(sessionId);
        actor = text(actor);
        agentId = text(agentId);
        agentDefinitionHash = text(agentDefinitionHash);
        executionHarness = text(executionHarness);
        harnessHash = text(harnessHash);
        engine = text(engine);
        adapterKey = text(adapterKey);
        modelId = text(modelId);
        modelProfileId = text(modelProfileId);
        promptIdentity = immutable(promptIdentity);
        requestIdentity = immutable(requestIdentity);
        requestPayload = immutable(requestPayload);
        metadata = immutable(metadata);
        attemptId = text(attemptId);
        leaseToken = text(leaseToken);
        workerId = text(workerId);
        if (startedAt == null || leaseExpiresAt == null) {
            throw new IllegalArgumentException("WORK_SESSION_START_TIME_REQUIRED");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
