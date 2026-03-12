package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogAuditPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Audit adapter for AI provider API catalog mutations. */
public final class OpsAiClientApiCatalogAdapter implements AiClientApiCatalogAuditPort {

    private static final String MODULE = "api-config";
    private static final String MASK = "******";

    private final OpsConfigAuditService auditService;

    public OpsAiClientApiCatalogAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void created(AiClientApiDefinition after) {
        auditService.record(MODULE, "create", after.apiId(), null, masked(after));
    }

    @Override
    public void updatedById(Long id, AiClientApiDefinition before, AiClientApiDefinition after) {
        auditService.record(MODULE, "update-by-id", String.valueOf(id), masked(before), masked(after));
    }

    @Override
    public void updatedByApiId(String apiId, AiClientApiDefinition before, AiClientApiDefinition after) {
        auditService.record(MODULE, "update-by-api-id", apiId, masked(before), masked(after));
    }

    @Override
    public void deletedById(Long id, AiClientApiDefinition before) {
        auditService.record(
                MODULE, "delete-by-id", String.valueOf(id), masked(before), Map.of("deleted", true));
    }

    @Override
    public void deletedByApiId(String apiId, AiClientApiDefinition before) {
        auditService.record(
                MODULE, "delete-by-api-id", apiId, masked(before), Map.of("deleted", true));
    }

    private AiClientApiResponseDTO masked(AiClientApiDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientApiResponseDTO.builder()
                .id(definition.id())
                .apiId(definition.apiId())
                .providerName(definition.providerName())
                .providerType(definition.providerType())
                .baseUrl(definition.baseUrl())
                .apiKey(hasText(definition.apiKey()) ? MASK : definition.apiKey())
                .completionsPath(definition.completionsPath())
                .embeddingsPath(definition.embeddingsPath())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
