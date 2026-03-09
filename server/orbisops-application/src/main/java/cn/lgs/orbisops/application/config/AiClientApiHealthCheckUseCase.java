package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;

/** Application process manager for one manual model-provider health check. */
public final class AiClientApiHealthCheckUseCase {

    public static final String TEST_TYPE = "MODELS_ENDPOINT";

    private final AiClientApiHealthTargetPort targetPort;
    private final AiClientApiHealthProbePort probePort;
    private final AiClientApiHealthOperatorPort operatorPort;
    private final AiClientApiHealthRecordPort recordPort;
    private final AiClientApiHealthAuditPort auditPort;
    private final Clock clock;

    public AiClientApiHealthCheckUseCase(
            AiClientApiHealthTargetPort targetPort,
            AiClientApiHealthProbePort probePort,
            AiClientApiHealthOperatorPort operatorPort,
            AiClientApiHealthRecordPort recordPort,
            AiClientApiHealthAuditPort auditPort) {
        this(targetPort, probePort, operatorPort, recordPort, auditPort, Clock.systemDefaultZone());
    }

    public AiClientApiHealthCheckUseCase(
            AiClientApiHealthTargetPort targetPort,
            AiClientApiHealthProbePort probePort,
            AiClientApiHealthOperatorPort operatorPort,
            AiClientApiHealthRecordPort recordPort,
            AiClientApiHealthAuditPort auditPort,
            Clock clock) {
        if (targetPort == null || probePort == null || operatorPort == null
                || recordPort == null || auditPort == null || clock == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_HEALTH_DEPENDENCY_REQUIRED");
        }
        this.targetPort = targetPort;
        this.probePort = probePort;
        this.operatorPort = operatorPort;
        this.recordPort = recordPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public AiClientApiHealthCheckResult check(String apiId) {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        String testedBy = text(operatorPort.currentOperator());
        AiClientApiHealthTarget target = targetPort.find(apiId);
        if (target == null) {
            AiClientApiHealthCheckResult missing = new AiClientApiHealthCheckResult(
                    apiId,
                    TEST_TYPE,
                    "",
                    "FAILED",
                    null,
                    0L,
                    "未找到对应的 API Provider",
                    testedBy,
                    checkedAt,
                    checkedAt);
            auditPort.record(missing);
            return missing;
        }
        AiClientApiHealthProbeOutcome outcome = probePort.probe(target);
        AiClientApiHealthProbeOutcome safe = outcome == null
                ? new AiClientApiHealthProbeOutcome(
                        "", "FAILED", null, 0L, "健康检查未返回结果")
                : outcome;
        AiClientApiHealthCheckResult result = new AiClientApiHealthCheckResult(
                target.apiId(),
                TEST_TYPE,
                safe.endpoint(),
                safe.status(),
                safe.httpStatus(),
                safe.latencyMs(),
                safe.errorMessage(),
                testedBy,
                checkedAt,
                checkedAt);
        recordPort.save(result);
        auditPort.record(result);
        return result;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
