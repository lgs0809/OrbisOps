package cn.lgs.orbisops.application.incident;

import java.util.Map;

/** Authoritative outcome of one recovery-verification attempt. */
public record IncidentVerificationResult(
        Status status,
        String summary,
        Map<String, Object> evidence) {

    public IncidentVerificationResult {
        status = status == null ? Status.INSUFFICIENT : status;
        summary = summary == null ? "" : summary.trim();
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public enum Status {
        PASSED,
        FAILED,
        INSUFFICIENT
    }

    public static IncidentVerificationResult insufficient(String summary, Map<String, Object> evidence) {
        return new IncidentVerificationResult(Status.INSUFFICIENT, summary, evidence);
    }
}
