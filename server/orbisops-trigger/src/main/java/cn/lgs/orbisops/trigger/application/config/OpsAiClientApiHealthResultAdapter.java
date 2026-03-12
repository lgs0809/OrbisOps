package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthAuditPort;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

/** Audit adapter for provider API health-check outcomes. */
public final class OpsAiClientApiHealthResultAdapter implements AiClientApiHealthAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsAiClientApiHealthResultAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void record(AiClientApiHealthCheckResult result) {
        auditService.record(
                "model-provider-health",
                "health-check",
                result.apiId(),
                null,
                result);
    }
}
