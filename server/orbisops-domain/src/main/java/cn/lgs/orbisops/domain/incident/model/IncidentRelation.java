package cn.lgs.orbisops.domain.incident.model;

public record IncidentRelation(
        String incidentId,
        String relatedIncidentId,
        String relationType,
        String createdBy,
        String createTime) {

    public IncidentRelation {
        incidentId = required(incidentId, "INCIDENT_ID_REQUIRED");
        relatedIncidentId = required(relatedIncidentId, "INCIDENT_RELATED_ID_REQUIRED");
        if (incidentId.equals(relatedIncidentId)) throw new IllegalArgumentException("INCIDENT_CANNOT_RELATE_SELF");
        relationType = value(relationType).isBlank() ? "RELATED" : value(relationType).toUpperCase(java.util.Locale.ROOT);
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
