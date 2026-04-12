package cn.lgs.orbisops.domain.analysis.model;

import java.time.Instant;

public record AnalysisTaskIncident(
        String incidentId,
        String title,
        String status,
        String severity,
        String serviceName,
        long occurrenceCount,
        Instant firstSeenAt,
        Instant lastSeenAt) {

    public AnalysisTaskIncident {
        incidentId = text(incidentId);
        title = text(title);
        status = text(status);
        severity = text(severity);
        serviceName = text(serviceName);
        occurrenceCount = Math.max(0L, occurrenceCount);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
