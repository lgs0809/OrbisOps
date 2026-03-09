package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogAuditPort;
import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.List;
import java.util.Map;

/** Trigger ACL for the legacy wide config repository and config-audit compatibility facade. */
public final class OpsAiClientApiCatalogAdapter
        implements AiClientApiCatalogPort, AiClientApiCatalogAuditPort {

    private static final String MODULE = "api-config";
    private static final String MASK = "******";

    private final IAiClientApiConfigRepository repository;
    private final OpsConfigAuditService auditService;

    public OpsAiClientApiCatalogAdapter(
            IAiClientApiConfigRepository repository,
            OpsConfigAuditService auditService) {
        if (repository == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CONFIG_REPOSITORY_REQUIRED");
        }
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    public boolean insert(AiClientApiDefinition definition) {
        return repository.insert(record(definition)) > 0;
    }

    @Override
    public boolean updateById(AiClientApiDefinition definition) {
        return repository.updateById(record(definition)) > 0;
    }

    @Override
    public boolean updateByApiId(AiClientApiDefinition definition) {
        return repository.updateByApiId(record(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByApiId(String apiId) {
        return repository.deleteByApiId(apiId) > 0;
    }

    @Override
    public AiClientApiDefinition findById(Long id) {
        return definition(repository.queryById(id));
    }

    @Override
    public AiClientApiDefinition findByApiId(String apiId) {
        return definition(repository.queryByApiId(apiId));
    }

    @Override
    public List<AiClientApiDefinition> listEnabled() {
        return definitions(repository.queryEnabledApis());
    }

    @Override
    public List<AiClientApiDefinition> listAll() {
        return definitions(repository.queryAll());
    }

    @Override
    public void created(AiClientApiDefinition after) {
        auditService.record(MODULE, "create", after.apiId(), null, masked(after));
    }

    @Override
    public void updatedById(
            Long id,
            AiClientApiDefinition before,
            AiClientApiDefinition after) {
        auditService.record(
                MODULE, "update-by-id", String.valueOf(id), masked(before), masked(after));
    }

    @Override
    public void updatedByApiId(
            String apiId,
            AiClientApiDefinition before,
            AiClientApiDefinition after) {
        auditService.record(
                MODULE, "update-by-api-id", apiId, masked(before), masked(after));
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

    private List<AiClientApiDefinition> definitions(List<AiClientConfigRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::definition).toList();
    }

    private AiClientApiDefinition definition(AiClientConfigRecord record) {
        if (record == null) {
            return null;
        }
        return new AiClientApiDefinition(
                record.getId(),
                record.getApiId(),
                record.getProviderName(),
                record.getProviderType(),
                record.getBaseUrl(),
                record.getApiKey(),
                record.getCompletionsPath(),
                record.getEmbeddingsPath(),
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private AiClientConfigRecord record(AiClientApiDefinition definition) {
        return AiClientConfigRecord.builder()
                .id(definition.id())
                .apiId(definition.apiId())
                .providerName(definition.providerName())
                .providerType(definition.providerType())
                .baseUrl(definition.baseUrl())
                .apiKey(definition.apiKey())
                .completionsPath(definition.completionsPath())
                .embeddingsPath(definition.embeddingsPath())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
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
