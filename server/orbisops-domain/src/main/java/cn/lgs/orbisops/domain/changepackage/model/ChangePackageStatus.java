package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;

public enum ChangePackageStatus {
    DRAFT,
    VALIDATING,
    VALIDATION_FAILED,
    REVISING,
    READY_FOR_REVIEW,
    REVIEWING,
    REJECTED,
    APPROVED,
    LANDING_RUNNING,
    LANDED,
    LANDING_FAILED,
    NEEDS_REPLAN,
    CLOSED;

    public static ChangePackageStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_REQUIRED");
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_UNKNOWN:" + normalized);
        }
    }

    public static ChangePackageStatus parseOrDraft(String value) {
        try {
            return require(value);
        } catch (IllegalArgumentException ignored) {
            return DRAFT;
        }
    }
}
