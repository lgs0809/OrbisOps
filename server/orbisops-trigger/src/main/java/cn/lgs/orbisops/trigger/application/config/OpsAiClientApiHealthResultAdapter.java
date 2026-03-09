package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiHealthAuditPort;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.application.config.AiClientApiHealthRecordPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiHealthCheckRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiClientApiHealthCheckRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.UUID;

/** Trigger ACL for health-check persistence and config-audit compatibility. */
public final class OpsAiClientApiHealthResultAdapter
        implements AiClientApiHealthRecordPort, AiClientApiHealthAuditPort {

    private final IAiClientApiHealthCheckRepository repository;
    private final OpsConfigAuditService auditService;

    public OpsAiClientApiHealthResultAdapter(
            IAiClientApiHealthCheckRepository repository,
            OpsConfigAuditService auditService) {
        if (repository == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_HEALTH_REPOSITORY_REQUIRED");
        }
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    public void save(AiClientApiHealthCheckResult result) {
        repository.save(new AiClientApiHealthCheckRecord(
                UUID.randomUUID().toString(),
                result.apiId(),
                result.testType(),
                result.endpoint(),
                result.status(),
                result.httpStatus(),
                result.latencyMs(),
                truncate(result.errorMessage(), 1000),
                result.testedBy(),
                result.testTime(),
                result.checkedAt()));
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
