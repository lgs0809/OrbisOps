package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillMutationMode {
    AUTO,
    MANUAL_ONLY,
    LOCKED,
    SEALED;

    public static SkillMutationMode require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_MUTATION_MODE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_MUTATION_MODE_UNKNOWN:" + normalized);
        }
    }

    public boolean ordinaryMutationAllowed() {
        return this == AUTO || this == MANUAL_ONLY;
    }

    public boolean autoPublishAllowed() {
        return this == AUTO;
    }
}
