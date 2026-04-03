package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientModelDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis adapter for the typed AI model catalog. */
@Repository
public class AiClientModelConfigRepository implements AiClientModelCatalogPort {

    private final ObjectProvider<IAiClientModelDao> daoProvider;

    public AiClientModelConfigRepository(ObjectProvider<IAiClientModelDao> daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public boolean insert(AiClientModelDefinition definition) {
        IAiClientModelDao dao = dao();
        return dao != null && dao.insert(toPo(definition)) > 0;
    }

    @Override
    public boolean updateById(AiClientModelDefinition definition) {
        IAiClientModelDao dao = dao();
        return dao != null && dao.updateById(toPo(definition)) > 0;
    }

    @Override
    public boolean updateByModelId(AiClientModelDefinition definition) {
        IAiClientModelDao dao = dao();
        return dao != null && dao.updateByModelId(toPo(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        IAiClientModelDao dao = dao();
        return dao != null && dao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByModelId(String modelId) {
        IAiClientModelDao dao = dao();
        return dao != null && dao.deleteByModelId(modelId) > 0;
    }

    @Override
    public AiClientModelDefinition findById(Long id) {
        IAiClientModelDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryById(id));
    }

    @Override
    public AiClientModelDefinition findByModelId(String modelId) {
        IAiClientModelDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryByModelId(modelId));
    }

    @Override
    public List<AiClientModelDefinition> findByApiId(String apiId) {
        IAiClientModelDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryByApiId(apiId));
    }

    @Override
    public List<AiClientModelDefinition> findByModelType(String modelType) {
        IAiClientModelDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryByModelType(modelType));
    }

    @Override
    public List<AiClientModelDefinition> listEnabled() {
        IAiClientModelDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryEnabledModels());
    }

    @Override
    public List<AiClientModelDefinition> listAll() {
        IAiClientModelDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryAll());
    }

    private IAiClientModelDao dao() {
        return daoProvider == null ? null : daoProvider.getIfAvailable();
    }

    private AiClientModel toPo(AiClientModelDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AI_CLIENT_MODEL_DEFINITION_REQUIRED");
        }
        return AiClientModel.builder()
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

    private AiClientModelDefinition toDefinition(AiClientModel po) {
        if (po == null) {
            return null;
        }
        return new AiClientModelDefinition(
                po.getId(),
                po.getModelId(),
                po.getApiId(),
                po.getModelName(),
                po.getModelType(),
                po.getModelUsage(),
                po.getDescription(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private List<AiClientModelDefinition> definitions(List<AiClientModel> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toDefinition).toList();
    }
}
