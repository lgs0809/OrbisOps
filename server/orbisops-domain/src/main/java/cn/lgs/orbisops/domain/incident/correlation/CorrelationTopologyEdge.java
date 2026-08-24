package cn.lgs.orbisops.domain.incident.correlation;

import java.time.Instant;

/** A scoped dependency from a configured inventory or a retained telemetry observation. */
public record CorrelationTopologyEdge(String source, String target, String evidenceRef,
                                      Instant observedAt, Instant expiresAt) {
    public CorrelationTopologyEdge {
        source = required(source); target = required(target); evidenceRef = required(evidenceRef);
        if (source.equals(target) || observedAt == null || expiresAt == null || !expiresAt.isAfter(observedAt))
            throw new IllegalArgumentException("CORRELATION_TOPOLOGY_INVALID");
    }

    public boolean covers(Instant time) {
        return time != null && !time.isBefore(observedAt) && !time.isAfter(expiresAt);
    }

    private static String required(String value) {
        if (value == null || value.isBlank() || value.length() > 240)
            throw new IllegalArgumentException("CORRELATION_TOPOLOGY_FIELD_INVALID");
        return value.trim();
    }
}
