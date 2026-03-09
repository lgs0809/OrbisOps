package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderAuditPort;
import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.List;
import java.util.Map;

/** Trigger ACL for the legacy RAG order repository and configuration audit protocol. */
public final class OpsRagOrderCatalogAdapter implements RagOrderCatalogPort, RagOrderAuditPort {

    private static final String AUDIT_MODULE = "rag-order";

    private final IAiClientRagOrderConfigRepository repository;
    private final OpsConfigAuditService auditService;

    public OpsRagOrderCatalogAdapter(
            IAiClientRagOrderConfigRepository repository,
            OpsConfigAuditService auditService) {
        if (repository == null) {
            throw new IllegalArgumentException("RAG_ORDER_REPOSITORY_REQUIRED");
        }
        if (auditService == null) {
            throw new IllegalArgumentException("RAG_ORDER_AUDIT_SERVICE_REQUIRED");
        }
        this.repository = repository;
        this.auditService = auditService;
    }

    @Override
    public boolean insert(RagOrderDefinition definition) {
        return repository.insert(record(definition)) > 0;
    }

    @Override
    public boolean updateById(RagOrderDefinition definition) {
        return repository.updateById(record(definition)) > 0;
    }

    @Override
    public boolean updateByRagId(RagOrderDefinition definition) {
        return repository.updateByRagId(record(definition)) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return repository.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByRagId(String ragId) {
        return repository.deleteByRagId(ragId) > 0;
    }

    @Override
    public RagOrderDefinition queryById(Long id) {
        return definition(repository.queryById(id));
    }

    @Override
    public RagOrderDefinition queryByRagId(String ragId) {
        return definition(repository.queryByRagId(ragId));
    }

    @Override
    public List<RagOrderDefinition> queryEnabled() {
        return definitions(repository.queryEnabledRagOrders());
    }

    @Override
    public List<RagOrderDefinition> queryByKnowledgeTag(String knowledgeTag) {
        return definitions(repository.queryByKnowledgeTag(knowledgeTag));
    }

    @Override
    public List<RagOrderDefinition> queryAll() {
        return definitions(repository.queryAll());
    }

    @Override
    public void created(RagOrderDefinition definition) {
        auditService.record(
                AUDIT_MODULE,
                "create",
                definition == null ? null : definition.ragId(),
                null,
                record(definition));
    }

    @Override
    public void updated(
            String action,
            String targetId,
            RagOrderDefinition before,
            RagOrderDefinition after) {
        auditService.record(
                AUDIT_MODULE,
                action,
                targetId,
                record(before),
                record(after));
    }

    @Override
    public void deleted(
            String action,
            String targetId,
            RagOrderDefinition before,
            boolean deleted) {
        auditService.record(
                AUDIT_MODULE,
                action,
                targetId,
                record(before),
                Map.of("deleted", deleted));
    }

    private List<RagOrderDefinition> definitions(List<AiClientConfigRecord> records) {
        return records == null || records.isEmpty()
                ? List.of()
                : records.stream().map(this::definition).toList();
    }

    private RagOrderDefinition definition(AiClientConfigRecord record) {
        if (record == null) {
            return null;
        }
        return new RagOrderDefinition(
                record.getId(),
                record.getRagId(),
                record.getRagName(),
                record.getKnowledgeTag(),
                record.getStatus(),
                record.getCreateTime(),
                record.getUpdateTime());
    }

    private AiClientConfigRecord record(RagOrderDefinition definition) {
        if (definition == null) {
            return null;
        }
        return AiClientConfigRecord.builder()
                .id(definition.id())
                .ragId(definition.ragId())
                .ragName(definition.ragName())
                .knowledgeTag(definition.knowledgeTag())
                .status(definition.status())
                .createTime(definition.createTime())
                .updateTime(definition.updateTime())
                .build();
    }
}
