package cn.lgs.orbisops.domain.execution.model;

import java.util.Locale;

public enum ExecutionResourceStatus {
    ENABLED,
    DISABLED;

    public static ExecutionResourceStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return ENABLED;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_STATUS_UNKNOWN:" + normalized);
        }
    }
}
