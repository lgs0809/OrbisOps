package cn.lgs.orbisops.domain.incident.model;

import java.util.List;
import java.util.Map;

public record IncidentAlertDraft(
        String incidentId,
        String projectId,
        String title,
        String severity,
        String serviceName,
        String sourceType,
        String fingerprint,
        String projectDedupKey,
        String runId,
        String summary,
        String labelsJson,
        Map<String, Object> metadata,
        List<String> affectedResources,
        boolean recovery) {

    public IncidentAlertDraft {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        projectId = required(projectId, "INCIDENT_PROJECT_ID_REQUIRED");
        title = required(title, "INCIDENT_TITLE_REQUIRED");
        severity = defaultText(severity, "WARN");
        serviceName = text(serviceName);
        sourceType = defaultText(sourceType, "ALERTMANAGER");
        fingerprint = text(fingerprint);
        projectDedupKey = required(projectDedupKey, "INCIDENT_ALERT_DEDUP_KEY_REQUIRED");
        runId = text(runId);
        summary = text(summary);
        labelsJson = defaultText(labelsJson, "{}");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
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
