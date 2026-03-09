package cn.lgs.orbisops.domain.incident.model;

import java.util.Map;

public record IncidentTimelineDraft(
        String incidentId,
        String eventType,
        String title,
        String detail,
        String actor,
        String refType,
        String refId,
        Map<String, Object> payload) {

    public IncidentTimelineDraft {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        eventType = defaultText(eventType, "NOTE").toUpperCase();
        title = defaultText(title, "记录");
        detail = text(detail);
        actor = required(actor, "INCIDENT_ACTOR_REQUIRED");
        refType = text(refType).toUpperCase();
        refId = text(refId);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
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
