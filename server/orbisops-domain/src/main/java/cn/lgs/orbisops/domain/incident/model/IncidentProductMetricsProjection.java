package cn.lgs.orbisops.domain.incident.model;

public record IncidentProductMetricsProjection(
        long incidentCount,
        long timelineEventCount,
        long weeklyHelpfulResolvedIncidents,
        long verificationSuccessCount,
        long commentCount,
        Long averageMttaMs,
        Long averageDiagnosisDurationMs,
        Long averageMttrMs) {

    public static IncidentProductMetricsProjection empty() {
        return new IncidentProductMetricsProjection(0, 0, 0, 0, 0, null, null, null);
    }
}
