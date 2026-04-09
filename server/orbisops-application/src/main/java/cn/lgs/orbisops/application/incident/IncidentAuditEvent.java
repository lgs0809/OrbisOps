package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;

import java.util.Map;

public record IncidentAuditEvent(
        String action,
        String incidentId,
        String actor,
        IncidentSnapshot before,
        IncidentSnapshot after,
        Map<String, Object> details) {

    public IncidentAuditEvent {
        action = required(action, "INCIDENT_AUDIT_ACTION_REQUIRED");
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        actor = required(actor, "INCIDENT_ACTOR_REQUIRED");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
