package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Persisted Skill Evolution signal projection. */
public record SkillEvolutionSignalSnapshot(
        String signalId,
        String idempotencyKey,
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String signalType,
        String payloadJson,
        String status,
        Instant createdAt) {

    public SkillEvolutionSignalSnapshot {
        signalId = value(signalId);
        idempotencyKey = value(idempotencyKey);
        projectId = value(projectId);
        agentId = value(agentId);
        runId = value(runId);
        sessionId = value(sessionId);
        signalType = value(signalType);
        payloadJson = payloadJson == null || payloadJson.trim().isBlank() ? "{}" : payloadJson.trim();
        status = value(status);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
