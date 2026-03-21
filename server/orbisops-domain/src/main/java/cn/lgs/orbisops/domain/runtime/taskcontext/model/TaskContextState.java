package cn.lgs.orbisops.domain.runtime.taskcontext.model;

import java.util.Locale;

public enum TaskContextState {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
    UNKNOWN;

    public static TaskContextState progress(String value) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return RUNNING;
        return switch (normalized) {
            case "SUCCEEDED", "DONE" -> COMPLETED;
            case "ERROR" -> FAILED;
            case "CANCELLED" -> CANCELED;
            default -> {
                try {
                    yield valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    yield RUNNING;
                }
            }
        };
    }

    public static TaskContextState terminal(String value) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCEEDED", "COMPLETED", "DONE" -> COMPLETED;
            case "FAILED", "ERROR" -> FAILED;
            case "CANCELED", "CANCELLED" -> CANCELED;
            default -> UNKNOWN;
        };
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
