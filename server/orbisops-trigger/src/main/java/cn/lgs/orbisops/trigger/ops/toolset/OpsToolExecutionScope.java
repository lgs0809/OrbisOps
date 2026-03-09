package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.Locale;

public enum OpsToolExecutionScope {
    PRE_APPROVAL_WORKFLOW,
    APPROVED_LANDING,
    SYSTEM_DISCOVERY;

    public static OpsToolExecutionScope from(Object value) {
        String text = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return PRE_APPROVAL_WORKFLOW;
        }
        return switch (text) {
            case "APPROVED_LANDING", "LANDING", "LANDING_RUNTIME" -> APPROVED_LANDING;
            case "SYSTEM_DISCOVERY", "DISCOVERY" -> SYSTEM_DISCOVERY;
            case "PRE_APPROVAL_WORKFLOW", "PRE_APPROVAL", "INVESTIGATE", "PREPARE" -> PRE_APPROVAL_WORKFLOW;
            default -> PRE_APPROVAL_WORKFLOW;
        };
    }
}
