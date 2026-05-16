package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.infrastructure.dao.IAiClientApiDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

/** MyBatis adapter for the typed AI provider API catalog. */
@Repository
public class AiClientApiConfigRepository implements AiClientApiCatalogPort {

    private final ObjectProvider<IAiClientApiDao> daoProvider;

    public AiClientApiConfigRepository(ObjectProvider<IAiClientApiDao> daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public boolean insert(AiClientApiDefinition definition) {
        IAiClientApiDao dao = dao();
        return dao != null && dao.insert(toPo(definition)) > 0;
    }

    @Override
    public boolean updateById(AiClientApiDefinition definition) {
        IAiClientApiDao dao = dao();
        return dao != null && dao.updateById(toPo(definition)) > 0;
    }

    @Override
    public boolean updateByApiId(AiClientApiDefinition definition) {
        IAiClientApiDao dao = dao();
        return dao != null && dao.updateByApiId(toPo(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        IAiClientApiDao dao = dao();
        return dao != null && dao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByApiId(String apiId) {
        IAiClientApiDao dao = dao();
        return dao != null && dao.deleteByApiId(apiId) > 0;
    }

    @Override
    public AiClientApiDefinition findById(Long id) {
        IAiClientApiDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryById(id));
    }

    @Override
    public AiClientApiDefinition findByApiId(String apiId) {
        IAiClientApiDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryByApiId(apiId));
    }

    @Override
    public List<AiClientApiDefinition> listEnabled() {
        IAiClientApiDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryEnabledApis());
    }

    @Override
    public List<AiClientApiDefinition> listAll() {
        IAiClientApiDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryAll());
    }

    private IAiClientApiDao dao() {
        return daoProvider == null ? null : daoProvider.getIfAvailable();
    }

    private AiClientApi toPo(AiClientApiDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_DEFINITION_REQUIRED");
        }
        return AiClientApi.builder()
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

    private AiClientApiDefinition toDefinition(AiClientApi po) {
        if (po == null) {
            return null;
        }
        return new AiClientApiDefinition(
                po.getId(),
                po.getApiId(),
                po.getProviderName(),
                po.getProviderType(),
                po.getBaseUrl(),
                po.getApiKey(),
                po.getCompletionsPath(),
                po.getEmbeddingsPath(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private List<AiClientApiDefinition> definitions(List<AiClientApi> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toDefinition).toList();
    }
}
