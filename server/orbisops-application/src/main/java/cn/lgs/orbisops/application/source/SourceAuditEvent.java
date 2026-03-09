package cn.lgs.orbisops.application.source;

public record SourceAuditEvent(
        String projectId,
        String module,
        String action,
        String targetId,
        Object before,
        Object after) {

    public SourceAuditEvent {
        projectId = value(projectId);
        module = required(module, "SOURCE_AUDIT_MODULE_REQUIRED");
        action = required(action, "SOURCE_AUDIT_ACTION_REQUIRED");
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
