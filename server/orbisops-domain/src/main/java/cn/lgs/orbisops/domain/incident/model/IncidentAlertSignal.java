package cn.lgs.orbisops.domain.incident.model;

public record IncidentAlertSignal(
        Long eventId,
        Long ruleId,
        String ruleName,
        String projectId,
        String sourceType,
        String eventStatus,
        String dedupKey,
        String fingerprint,
        String alertName,
        String severity,
        String serviceName,
        String runId,
        String finalSummary,
        String labelsJson) {

    public IncidentAlertSignal {
        projectId = required(projectId, "INCIDENT_PROJECT_ID_REQUIRED");
        dedupKey = required(dedupKey, "INCIDENT_ALERT_DEDUP_KEY_REQUIRED");
        ruleName = text(ruleName);
        sourceType = defaultText(sourceType, "ALERTMANAGER").toUpperCase();
        eventStatus = text(eventStatus).toUpperCase();
        fingerprint = text(fingerprint);
        alertName = defaultText(alertName, "生产告警触发事件");
        severity = defaultText(severity, "WARN").toUpperCase();
        serviceName = text(serviceName);
        runId = text(runId);
        finalSummary = text(finalSummary);
        labelsJson = defaultText(labelsJson, "{}");
    }

    public boolean recovery() {
        return eventStatus.startsWith("RECOVERY_");
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String defaultText(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
