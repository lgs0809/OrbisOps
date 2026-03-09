package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillEvolutionJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    NO_CHANGE,
    FAILED;

    public static SkillEvolutionJobStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_STATUS_UNKNOWN:" + normalized);
        }
    }
}
