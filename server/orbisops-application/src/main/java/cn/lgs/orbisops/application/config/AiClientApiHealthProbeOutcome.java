package cn.lgs.orbisops.application.config;

/** Sanitized technical result returned by the provider health protocol adapter. */
public record AiClientApiHealthProbeOutcome(
        String endpoint,
        String status,
        Integer httpStatus,
        long latencyMs,
        String errorMessage) {

    public AiClientApiHealthProbeOutcome {
        endpoint = text(endpoint);
        status = text(status);
        latencyMs = Math.max(0L, latencyMs);
        errorMessage = text(errorMessage);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
