package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.application.rag.RagTagOrderPort;
import cn.lgs.orbisops.infrastructure.dao.IAiClientRagOrderDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiClientRagOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** MyBatis adapter for the typed knowledge-base catalog. */
@Repository
public class AiClientRagOrderConfigRepository implements RagOrderCatalogPort, RagTagOrderPort {

    private final ObjectProvider<IAiClientRagOrderDao> daoProvider;

    public AiClientRagOrderConfigRepository(ObjectProvider<IAiClientRagOrderDao> daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public boolean insert(RagOrderDefinition definition) {
        IAiClientRagOrderDao dao = dao();
        return dao != null && dao.insert(toPo(definition)) > 0;
    }

    @Override
    public boolean updateById(RagOrderDefinition definition) {
        IAiClientRagOrderDao dao = dao();
        return dao != null && dao.updateById(toPo(definition)) > 0;
    }

    @Override
    public boolean updateByRagId(RagOrderDefinition definition) {
        IAiClientRagOrderDao dao = dao();
        return dao != null && dao.updateByRagId(toPo(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        IAiClientRagOrderDao dao = dao();
        return dao != null && dao.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByRagId(String ragId) {
        IAiClientRagOrderDao dao = dao();
        return dao != null && dao.deleteByRagId(ragId) > 0;
    }

    @Override
    public RagOrderDefinition queryById(Long id) {
        IAiClientRagOrderDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryById(id));
    }

    @Override
    public RagOrderDefinition queryByRagId(String ragId) {
        IAiClientRagOrderDao dao = dao();
        return dao == null ? null : toDefinition(dao.queryByRagId(ragId));
    }

    @Override
    public List<RagOrderDefinition> queryEnabled() {
        IAiClientRagOrderDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryEnabledRagOrders());
    }

    @Override
    public List<RagOrderDefinition> queryByKnowledgeTag(String knowledgeTag) {
        IAiClientRagOrderDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryByKnowledgeTag(knowledgeTag));
    }

    @Override
    public List<RagOrderDefinition> queryAll() {
        IAiClientRagOrderDao dao = dao();
        return dao == null ? List.of() : definitions(dao.queryAll());
    }

    @Override
    public void create(String ragName, String knowledgeTag) {
        String normalizedName = required(ragName, "RAG_NAME_REQUIRED");
        String normalizedTag = required(knowledgeTag, "RAG_KNOWLEDGE_TAG_REQUIRED");
        IAiClientRagOrderDao dao = dao();
        if (dao == null) {
            return;
        }
        dao.insert(AiClientRagOrder.builder()
                .ragId("RAG_" + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16)
                        .toUpperCase(Locale.ROOT))
                .ragName(normalizedName)
                .knowledgeTag(normalizedTag)
                .status(1)
                .build());
    }

    private IAiClientRagOrderDao dao() {
        return daoProvider == null ? null : daoProvider.getIfAvailable();
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(reasonCode);
        }
        return normalized;
    }

    private AiClientRagOrder toPo(RagOrderDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("RAG_ORDER_DEFINITION_REQUIRED");
        }
        return AiClientRagOrder.builder()
                .id(definition.id())
                .ragId(definition.ragId())
                .ragName(definition.ragName())
                .knowledgeTag(definition.knowledgeTag())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }

    private RagOrderDefinition toDefinition(AiClientRagOrder po) {
        if (po == null) {
            return null;
        }
        return new RagOrderDefinition(
                po.getId(),
                po.getRagId(),
                po.getRagName(),
                po.getKnowledgeTag(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    private List<RagOrderDefinition> definitions(List<AiClientRagOrder> rows) {
        return rows == null || rows.isEmpty()
                ? List.of()
                : rows.stream().map(this::toDefinition).toList();
    }
}
