package cn.lgs.orbisops.domain.project.model;

import java.util.Locale;

public enum ProjectMemberStatus {
    ENABLED,
    DISABLED;

    public static ProjectMemberStatus parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return ENABLED;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return DISABLED;
        }
    }
}
