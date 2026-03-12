package cn.lgs.orbisops.types.execution;

import java.util.Locale;

/** Product-level execution choice shared by Chat, Channel, Schedule and Alert entry points. */
public enum ExecutionType {
    NONE,
    REACT,
    WORKFLOW;

    public static ExecutionType parse(String value, ExecutionType fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static ExecutionType require(String value) {
        ExecutionType result = parse(value, null);
        if (result == null) throw new IllegalArgumentException("EXECUTION_TYPE_INVALID");
        return result;
    }
}
