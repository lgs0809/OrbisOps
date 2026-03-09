package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncCatalogPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;

/** Repository ACL for Provider model synchronization without per-model audit. */
public final class OpsAiClientModelSyncCatalogAdapter implements AiClientModelSyncCatalogPort {

    private final IAiClientModelConfigRepository repository;

    public OpsAiClientModelSyncCatalogAdapter(IAiClientModelConfigRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_CONFIG_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public AiClientModelDefinition findByModelId(String modelId) {
        return definition(repository.queryByModelId(modelId));
    }

    @Override
    public boolean insert(AiClientModelDefinition definition) {
        return repository.insert(record(definition)) > 0;
    }

    @Override
    public boolean updateByModelId(AiClientModelDefinition definition) {
        return repository.updateByModelId(record(definition)) > 0;
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
