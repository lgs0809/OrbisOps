package cn.lgs.orbisops.domain.incident.model;

public record IncidentTimelineEntry(
        Long id,
        String incidentId,
        String eventType,
        String title,
        String detail,
        String actor,
        String refType,
        String refId,
        String payloadJson,
        String createTime) {

    public IncidentTimelineEntry {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        eventType = defaultText(eventType, "NOTE");
        title = defaultText(title, "记录");
        detail = text(detail);
        actor = text(actor);
        refType = text(refType);
        refId = text(refId);
        payloadJson = text(payloadJson);
        createTime = text(createTime);
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
