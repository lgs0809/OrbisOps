package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillExecutionMode {
    ENABLED,
    SHADOW_ONLY,
    QUARANTINED,
    DISABLED;

    public static SkillExecutionMode require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_EXECUTION_MODE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_EXECUTION_MODE_UNKNOWN:" + normalized);
        }
    }

    public boolean formalRuntimeEnabled() {
        return this == ENABLED;
    }
}
