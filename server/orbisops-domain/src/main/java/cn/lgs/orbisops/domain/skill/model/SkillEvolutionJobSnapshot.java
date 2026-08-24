package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Persisted Skill Evolution job projection. */
public record SkillEvolutionJobSnapshot(
        long databaseId,
        String jobId,
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String triggerReason,
        SkillEvolutionJobStatus status,
        int attempts,
        Instant nextRunAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        String sourceId,
        String leaseToken,
        long epoch,
        long leaseUntilMillis,
        int ordinaryFailures) {

    /** Compatibility for callers without a persisted classified failure counter. */
    public SkillEvolutionJobSnapshot(long databaseId, String jobId, String runId, String sessionId,
            String projectId, String agentId, String triggerReason, SkillEvolutionJobStatus status,
            int attempts, Instant nextRunAt, String lastError, Instant createdAt, Instant updatedAt,
            String sourceId, String leaseToken, long epoch, long leaseUntilMillis) {
        this(databaseId, jobId, runId, sessionId, projectId, agentId, triggerReason, status, attempts,
                nextRunAt, lastError, createdAt, updatedAt, sourceId, leaseToken, epoch, leaseUntilMillis, attempts);
    }

    public SkillEvolutionJobSnapshot(long databaseId, String jobId, String runId, String sessionId,
            String projectId, String agentId, String triggerReason, SkillEvolutionJobStatus status,
            int attempts, Instant nextRunAt, String lastError, Instant createdAt, Instant updatedAt) {
        this(databaseId, jobId, runId, sessionId, projectId, agentId, triggerReason, status, attempts,
                nextRunAt, lastError, createdAt, updatedAt, "", "", 0, 0);
    }

    public SkillEvolutionJobSnapshot {
        jobId = value(jobId);
        runId = value(runId);
        sessionId = value(sessionId);
        projectId = value(projectId);
        agentId = value(agentId);
        triggerReason = value(triggerReason);
        if (status == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_STATUS_REQUIRED");
        attempts = Math.max(0, attempts);
        ordinaryFailures = Math.max(0, ordinaryFailures);
        lastError = value(lastError);
        sourceId = value(sourceId);
        leaseToken = value(leaseToken);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
