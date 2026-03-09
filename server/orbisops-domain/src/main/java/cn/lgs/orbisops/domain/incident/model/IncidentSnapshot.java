package cn.lgs.orbisops.domain.incident.model;

public record IncidentSnapshot(
        Long id,
        String incidentId,
        String projectId,
        String title,
        IncidentStatus status,
        String severity,
        String serviceName,
        String sourceType,
        String fingerprint,
        String dedupKey,
        String currentRunId,
        String ownerUserId,
        String summary,
        String labelsJson,
        String metadataJson,
        long occurrenceCount,
        String affectedResourcesJson,
        String firstSeenAt,
        String lastSeenAt,
        String createTime,
        String updateTime,
        String acknowledgedAt,
        String resolvedAt,
        String reviewedAt) {

    public IncidentSnapshot {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        projectId = required(projectId, "INCIDENT_PROJECT_ID_REQUIRED");
        title = required(title, "INCIDENT_TITLE_REQUIRED");
        if (status == null) throw new IllegalArgumentException("INCIDENT_STATUS_REQUIRED");
        severity = defaultText(severity, "WARN");
        serviceName = text(serviceName);
        sourceType = defaultText(sourceType, "MANUAL");
        fingerprint = text(fingerprint);
        dedupKey = text(dedupKey);
        currentRunId = text(currentRunId);
        ownerUserId = text(ownerUserId);
        summary = text(summary);
        labelsJson = defaultText(labelsJson, "{}");
        metadataJson = defaultText(metadataJson, "{}");
        occurrenceCount = Math.max(0L, occurrenceCount);
        affectedResourcesJson = defaultText(affectedResourcesJson, "[]");
        firstSeenAt = text(firstSeenAt);
        lastSeenAt = text(lastSeenAt);
        createTime = text(createTime);
        updateTime = text(updateTime);
        acknowledgedAt = text(acknowledgedAt);
        resolvedAt = text(resolvedAt);
        reviewedAt = text(reviewedAt);
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
