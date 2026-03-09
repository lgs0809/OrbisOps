package cn.lgs.orbisops.types.execution;

import java.util.Locale;

public enum ExecutionVersionPolicy {
    LATEST_PUBLISHED,
    PINNED_VERSION;

    public static ExecutionVersionPolicy parse(String value, ExecutionVersionPolicy fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static ExecutionVersionPolicy require(String value) {
        ExecutionVersionPolicy result = parse(value, null);
        if (result == null) throw new IllegalArgumentException("EXECUTION_VERSION_POLICY_INVALID");
        return result;
    }
}
