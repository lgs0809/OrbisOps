package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    FROZEN,
    DEPRECATED,
    RETIRED,
    REJECTED,
    ENABLED,
    DISABLED;

    public static SkillStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_STATUS_UNKNOWN:" + normalized);
        }
    }

    public boolean active() {
        return this == ACTIVE || this == ENABLED;
    }
}
