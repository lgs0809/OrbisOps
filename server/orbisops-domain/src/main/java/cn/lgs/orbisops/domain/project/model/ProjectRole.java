package cn.lgs.orbisops.domain.project.model;

import java.util.Locale;

public enum ProjectRole {
    OWNER,
    REVIEWER,
    APPROVER,
    OPERATOR,
    MAINTAINER,
    OPS_LEAD,
    ADMIN,
    BREAK_GLASS_ADMIN,
    MEMBER,
    VIEWER,
    NONE;

    public static ProjectRole parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return NONE;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
