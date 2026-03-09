package cn.lgs.orbisops.application.changepackage;

import java.util.Locale;

/** Durable Landing run lifecycle state owned by the Application process manager. */
public enum ChangePackageLandingRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    NEEDS_REPLAN;

    public static ChangePackageLandingRunStatus from(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RUNNING" -> RUNNING;
            case "SUCCEEDED", "LANDED" -> SUCCEEDED;
            case "NEEDS_REPLAN" -> NEEDS_REPLAN;
            case "FAILED", "LANDING_FAILED" -> FAILED;
            default -> throw new IllegalArgumentException(
                    "CHANGE_PACKAGE_LANDING_RUN_STATUS_INVALID:" + normalized);
        };
    }

    public boolean succeeded() {
        return this == SUCCEEDED;
    }
}
