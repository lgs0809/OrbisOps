package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillLifecycleStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    DEPRECATED,
    RETIRED;

    public static SkillLifecycleStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_LIFECYCLE_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_LIFECYCLE_STATUS_UNKNOWN:" + normalized);
        }
    }
}
