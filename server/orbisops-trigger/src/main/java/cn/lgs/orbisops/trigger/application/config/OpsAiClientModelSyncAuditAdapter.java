package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientModelSyncResponseDTO;
import cn.lgs.orbisops.application.config.AiClientModelSyncAuditPort;
import cn.lgs.orbisops.application.config.AiClientModelSyncResult;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

/** Compatibility audit projector for Provider model synchronization. */
public final class OpsAiClientModelSyncAuditAdapter implements AiClientModelSyncAuditPort {

    private static final String MODULE = "model-config";

    private final OpsConfigAuditService auditService;

    public OpsAiClientModelSyncAuditAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void succeeded(AiClientModelSyncResult result) {
        auditService.record(MODULE, "sync-from-provider", result.apiId(), null, response(result));
    }

    @Override
    public void failed(AiClientModelSyncResult result) {
        auditService.record(MODULE, "sync-from-provider-failed", result.apiId(), null, response(result));
    }

    private AiClientModelSyncResponseDTO response(AiClientModelSyncResult result) {
        return AiClientModelSyncResponseDTO.builder()
                .apiId(result.apiId())
                .endpoint(result.endpoint())
                .httpStatus(result.httpStatus())
                .fetchedCount(result.fetchedCount())
                .createdCount(result.createdCount())
                .updatedCount(result.updatedCount())
                .skippedCount(result.skippedCount())
                .modelIds(result.modelIds())
                .errorMessage(result.errorMessage())
                .syncedAt(result.syncedAt())
                .build();
    }
}
