package cn.lgs.orbisops.application.repair;

public record RepairAuditEvent(
        String projectId,
        String action,
        String targetId,
        Object before,
        Object after) {

    public RepairAuditEvent {
        projectId = value(projectId);
        action = required(action, "REPAIR_AUDIT_ACTION_REQUIRED");
        targetId = value(targetId);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
