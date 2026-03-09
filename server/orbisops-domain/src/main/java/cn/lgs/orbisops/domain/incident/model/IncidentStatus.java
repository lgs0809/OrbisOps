package cn.lgs.orbisops.domain.incident.model;

import java.util.Locale;

/** Business status exposed by the Incident read model. */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    ACTION_REQUIRED,
    REMEDIATING,
    VERIFYING,
    RESOLVED,
    CLOSED;

    public static IncidentStatus require(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("INCIDENT_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("INCIDENT_STATUS_UNKNOWN:" + normalized);
        }
    }

    /**
     * Reads rows written by the pre-productization Incident state machine without
     * exposing those historical states as current product semantics.
     */
    public static IncidentStatus fromStored(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "ACKED" -> INVESTIGATING;
            case "MITIGATED" -> VERIFYING;
            case "REVIEWED" -> CLOSED;
            default -> require(normalized);
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
