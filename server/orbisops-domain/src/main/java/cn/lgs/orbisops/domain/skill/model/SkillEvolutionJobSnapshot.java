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
        Instant updatedAt) {

    public SkillEvolutionJobSnapshot {
        jobId = value(jobId);
        runId = value(runId);
        sessionId = value(sessionId);
        projectId = value(projectId);
        agentId = value(agentId);
        triggerReason = value(triggerReason);
        if (status == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_STATUS_REQUIRED");
        attempts = Math.max(0, attempts);
        lastError = value(lastError);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
