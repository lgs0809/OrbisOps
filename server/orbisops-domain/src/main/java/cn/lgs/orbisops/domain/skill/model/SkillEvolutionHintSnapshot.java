package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Persisted Skill Evolution hint projection. */
public record SkillEvolutionHintSnapshot(
        String hintId,
        String signalId,
        String projectId,
        String runId,
        String hintType,
        String contentJson,
        String status,
        Instant createdAt) {

    public SkillEvolutionHintSnapshot {
        hintId = value(hintId);
        signalId = value(signalId);
        projectId = value(projectId);
        runId = value(runId);
        hintType = value(hintType);
        contentJson = contentJson == null || contentJson.trim().isBlank() ? "{}" : contentJson.trim();
        status = value(status);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
