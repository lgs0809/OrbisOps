package cn.lgs.orbisops.domain.incident.correlation;

import java.time.Instant;
import java.util.Map;

/** Normalized alert facts. Alert text is evidence, never an executable instruction. */
public record CorrelationSignal(long eventId, String incidentId, String projectId, String environment,
                                String entityId, Instant startedAt, Instant receivedAt,
                                Map<String, String> identities, String title, boolean recovery) {
    public CorrelationSignal {
        if (eventId < 1 || incidentId == null || incidentId.isBlank() || projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("CORRELATION_SIGNAL_ID_REQUIRED");
        environment = text(environment);
        entityId = text(entityId);
        title = text(title);
        identities = identities == null ? Map.of() : Map.copyOf(identities);
        if (receivedAt == null) throw new IllegalArgumentException("CORRELATION_RECEIVED_AT_REQUIRED");
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
