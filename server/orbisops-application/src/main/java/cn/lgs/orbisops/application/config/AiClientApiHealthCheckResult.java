package cn.lgs.orbisops.application.config;

import java.time.LocalDateTime;

/** Typed result of one API provider health check. */
public record AiClientApiHealthCheckResult(
        String apiId,
        String testType,
        String endpoint,
        String status,
        Integer httpStatus,
        long latencyMs,
        String errorMessage,
        String testedBy,
        LocalDateTime testTime,
        LocalDateTime checkedAt) {

    public AiClientApiHealthCheckResult {
        apiId = text(apiId);
        testType = text(testType);
        endpoint = text(endpoint);
        status = text(status);
        latencyMs = Math.max(0L, latencyMs);
        errorMessage = text(errorMessage);
        testedBy = text(testedBy);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
