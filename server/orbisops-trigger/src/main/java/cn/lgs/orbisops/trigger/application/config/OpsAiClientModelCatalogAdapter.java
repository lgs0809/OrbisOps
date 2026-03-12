package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogAuditPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Audit adapter for AI model catalog mutations. */
public final class OpsAiClientModelCatalogAdapter implements AiClientModelCatalogAuditPort {

    private static final String MODULE = "model-config";

    private final OpsConfigAuditService auditService;

    public OpsAiClientModelCatalogAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void created(AiClientModelDefinition created) {
        auditService.record(MODULE, "create", created.modelId(), null, created);
    }

    @Override
    public void updatedById(Long id, AiClientModelDefinition before, AiClientModelDefinition after) {
        auditService.record(MODULE, "update-by-id", String.valueOf(id), before, after);
    }

    @Override
    public void updatedByModelId(String modelId, AiClientModelDefinition before, AiClientModelDefinition after) {
        auditService.record(MODULE, "update-by-model-id", modelId, before, after);
    }

    @Override
    public void deletedById(Long id, AiClientModelDefinition before) {
        auditService.record(MODULE, "delete-by-id", String.valueOf(id), before, Map.of("deleted", true));
    }

    @Override
    public void deletedByModelId(String modelId, AiClientModelDefinition before) {
        auditService.record(MODULE, "delete-by-model-id", modelId, before, Map.of("deleted", true));
    }
}
