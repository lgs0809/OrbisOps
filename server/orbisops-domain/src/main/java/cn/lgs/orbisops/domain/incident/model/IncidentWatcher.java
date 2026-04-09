package cn.lgs.orbisops.domain.incident.model;

public record IncidentWatcher(
        String incidentId,
        String userId,
        String createdBy,
        String createTime) {

    public IncidentWatcher {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        userId = required(userId, "INCIDENT_WATCHER_USER_ID_REQUIRED");
        createdBy = value(createdBy);
        createTime = value(createTime);
    }

    private static String required(String input, String reason) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
