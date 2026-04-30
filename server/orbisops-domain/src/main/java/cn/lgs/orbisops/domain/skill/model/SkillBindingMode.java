package cn.lgs.orbisops.domain.skill.model;

import java.util.Locale;

public enum SkillBindingMode {
    FLOATING,
    PINNED;

    public static SkillBindingMode require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_BINDING_MODE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SKILL_BINDING_MODE_UNKNOWN:" + normalized);
        }
    }
}
