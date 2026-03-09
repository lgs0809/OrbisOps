package cn.lgs.orbisops.domain.incident.model;

import java.util.List;
import java.util.Map;

public record IncidentDraft(
        String incidentId,
        String projectId,
        String title,
        IncidentStatus status,
        String severity,
        String serviceName,
        String sourceType,
        String summary,
        Map<String, Object> labels,
        Map<String, Object> metadata,
        List<String> affectedResources) {

    public IncidentDraft {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        projectId = required(projectId, "INCIDENT_PROJECT_ID_REQUIRED");
        title = required(title, "INCIDENT_TITLE_REQUIRED");
        if (status == null) throw new IllegalArgumentException("INCIDENT_STATUS_REQUIRED");
        severity = defaultText(severity, "WARN").toUpperCase();
        serviceName = text(serviceName);
        sourceType = defaultText(sourceType, "MANUAL").toUpperCase();
        summary = text(summary);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
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
