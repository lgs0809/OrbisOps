package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogAuditPort;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.List;
import java.util.Map;

/** Trigger ACL for the legacy wide model repository and config-audit facade. */
public final class OpsAiClientModelCatalogAdapter
        implements AiClientModelCatalogPort, AiClientModelCatalogAuditPort {

    private static final String MODULE = "model-config";

    private final IAiClientModelConfigRepository repository;
    private final OpsConfigAuditService auditService;

    public OpsAiClientModelCatalogAdapter(
            IAiClientModelConfigRepository repository,
            OpsConfigAuditService auditService) {
        if (repository == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CONFIG_REPOSITORY_REQUIRED");
        }
        if (auditService == null) {
            throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        }
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    public boolean insert(AiClientModelDefinition definition) {
        return repository.insert(record(definition)) > 0;
    }

    @Override
    public boolean updateById(AiClientModelDefinition definition) {
        return repository.updateById(record(definition)) > 0;
    }

    @Override
    public boolean updateByModelId(AiClientModelDefinition definition) {
        return repository.updateByModelId(record(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByModelId(String modelId) {
        return repository.deleteByModelId(modelId) > 0;
    }

    @Override
    public AiClientModelDefinition findById(Long id) {
        return definition(repository.queryById(id));
    }

    @Override
    public AiClientModelDefinition findByModelId(String modelId) {
        return definition(repository.queryByModelId(modelId));
    }

    @Override
    public List<AiClientModelDefinition> findByApiId(String apiId) {
        return definitions(repository.queryByApiId(apiId));
    }

    @Override
    public List<AiClientModelDefinition> findByModelType(String modelType) {
        return definitions(repository.queryByModelType(modelType));
    }

    @Override
    public List<AiClientModelDefinition> listEnabled() {
        return definitions(repository.queryEnabledModels());
    }

    @Override
    public List<AiClientModelDefinition> listAll() {
        return definitions(repository.queryAll());
    }

    @Override
    public void created(AiClientModelDefinition created) {
        auditService.record(MODULE, "create", created.modelId(), null, record(created));
    }

    @Override
    public void updatedById(
            Long id,
            AiClientModelDefinition before,
            AiClientModelDefinition after) {
        auditService.record(
                MODULE, "update-by-id", String.valueOf(id), record(before), record(after));
    }

    @Override
    public void updatedByModelId(
            String modelId,
            AiClientModelDefinition before,
            AiClientModelDefinition after) {
        auditService.record(
                MODULE, "update-by-model-id", modelId, record(before), record(after));
    }

    @Override
    public void deletedById(Long id, AiClientModelDefinition before) {
        auditService.record(
                MODULE, "delete-by-id", String.valueOf(id), record(before), Map.of("deleted", true));
    }

    @Override
    public void deletedByModelId(String modelId, AiClientModelDefinition before) {
        auditService.record(
                MODULE, "delete-by-model-id", modelId, record(before), Map.of("deleted", true));
    }

    private List<AiClientModelDefinition> definitions(List<AiClientConfigRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::definition).toList();
    }

    private AiClientModelDefinition definition(AiClientConfigRecord record) {
        if (record == null) {
            return null;
        }
        return new AiClientModelDefinition(
                record.getId(),
                record.getModelId(),
                record.getApiId(),
                record.getModelName(),
                record.getModelType(),
                record.getModelUsage(),
                record.getDescription(),
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private AiClientConfigRecord record(AiClientModelDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientConfigRecord.builder()
                .id(definition.id())
                .modelId(definition.modelId())
                .apiId(definition.apiId())
                .modelName(definition.modelName())
                .modelType(definition.modelType())
                .modelUsage(definition.modelUsage())
                .description(definition.description())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
