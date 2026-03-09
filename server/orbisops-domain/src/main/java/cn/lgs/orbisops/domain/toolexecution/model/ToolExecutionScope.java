package cn.lgs.orbisops.domain.toolexecution.model;

import java.util.Locale;

public enum ToolExecutionScope {
    PRE_APPROVAL_WORKFLOW,
    APPROVED_LANDING,
    SYSTEM_DISCOVERY;

    public static ToolExecutionScope from(Object value) {
        String normalized = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED_LANDING", "LANDING", "LANDING_RUNTIME" -> APPROVED_LANDING;
            case "SYSTEM_DISCOVERY", "DISCOVERY" -> SYSTEM_DISCOVERY;
            default -> PRE_APPROVAL_WORKFLOW;
        };
    }
}
