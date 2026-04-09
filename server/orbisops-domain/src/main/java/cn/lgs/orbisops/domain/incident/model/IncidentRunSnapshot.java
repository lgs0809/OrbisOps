package cn.lgs.orbisops.domain.incident.model;

public record IncidentRunSnapshot(
        String runId,
        String status,
        String errorMessage,
        String createdAt,
        String updatedAt,
        Long durationMs) {

    public IncidentRunSnapshot {
        runId = required(runId, "INCIDENT_RUN_ID_REQUIRED");
        status = text(status);
        errorMessage = text(errorMessage);
        createdAt = text(createdAt);
        updatedAt = text(updatedAt);
        durationMs = durationMs == null ? null : Math.max(0L, durationMs);
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
