package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Persisted Skill Evolution patch projection. */
public record SkillEvolutionPatchSnapshot(
        long databaseId,
        String patchId,
        String jobId,
        String runId,
        String projectId,
        String targetSkillId,
        String decision,
        String patchJson,
        String validationJson,
        String status,
        Integer appliedVersion,
        String skippedReason,
        Instant createdAt,
        Instant updatedAt) {

    public SkillEvolutionPatchSnapshot {
        patchId = value(patchId);
        jobId = value(jobId);
        runId = value(runId);
        projectId = value(projectId);
        targetSkillId = value(targetSkillId);
        decision = value(decision);
        patchJson = json(patchJson);
        validationJson = json(validationJson);
        status = value(status);
        skippedReason = value(skippedReason);
    }

    private static String json(String value) {
        String normalized = value(value);
        return normalized.isBlank() ? "{}" : normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
