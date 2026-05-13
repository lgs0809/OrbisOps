package cn.lgs.orbisops.application.rag;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for legacy RAG order configuration CRUD and query. */
public final class RagOrderCatalogUseCase {

    private final RagOrderCatalogPort catalogPort;
    private final RagOrderAuditPort auditPort;
    private final Clock clock;

    public RagOrderCatalogUseCase(
            RagOrderCatalogPort catalogPort,
            RagOrderAuditPort auditPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("RAG_ORDER_CATALOG_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("RAG_ORDER_AUDIT_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("RAG_ORDER_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    public boolean create(RagOrderDefinition definition) {
        RagOrderDefinition created = requireDefinition(definition).createdAt(now());
        boolean result = catalogPort.insert(created);
        auditPort.created(created);
        return result;
    }

    public boolean updateById(RagOrderDefinition definition) {
        RagOrderDefinition update = requireDefinition(definition).updatedAt(now());
        RagOrderDefinition before = catalogPort.queryById(update.id());
        boolean result = catalogPort.updateById(update);
        auditPort.updated("update-by-id", String.valueOf(update.id()), before, update);
        return result;
    }

    public boolean updateByRagId(RagOrderDefinition definition) {
        RagOrderDefinition update = requireDefinition(definition).updatedAt(now());
        RagOrderDefinition before = catalogPort.queryByRagId(update.ragId());
        boolean result = catalogPort.updateByRagId(update);
        auditPort.updated("update-by-rag-id", update.ragId(), before, update);
        return result;
    }

    public boolean deleteById(Long id) {
        RagOrderDefinition before = catalogPort.queryById(id);
        boolean deleted = catalogPort.deleteById(id);
        auditPort.deleted("delete-by-id", String.valueOf(id), before, deleted);
        return deleted;
    }

    public boolean deleteByRagId(String ragId) {
        RagOrderDefinition before = catalogPort.queryByRagId(ragId);
        boolean deleted = catalogPort.deleteByRagId(ragId);
        auditPort.deleted("delete-by-rag-id", ragId, before, deleted);
        return deleted;
    }

    public RagOrderDefinition queryById(Long id) {
        return catalogPort.queryById(id);
    }

    public RagOrderDefinition queryByRagId(String ragId) {
        return catalogPort.queryByRagId(ragId);
    }

    public List<RagOrderDefinition> queryEnabled() {
        return immutable(catalogPort.queryEnabled());
    }

    public List<RagOrderDefinition> queryByKnowledgeTag(String knowledgeTag) {
        return immutable(catalogPort.queryByKnowledgeTag(knowledgeTag));
    }

    public List<RagOrderDefinition> queryByStatus(Integer status) {
        if (status == null) {
            return List.of();
        }
        return queryAll().stream()
                .filter(order -> status.equals(order.status()))
                .toList();
    }

    public List<RagOrderDefinition> queryAll() {
        return immutable(catalogPort.queryAll());
    }

    public List<RagOrderDefinition> queryList(RagOrderCatalogQuery query) {
        RagOrderCatalogQuery safe = query == null ? RagOrderCatalogQuery.all() : query;
        List<RagOrderDefinition> filtered = queryAll().stream()
                .filter(order -> !hasText(safe.ragId()) || contains(order.ragId(), safe.ragId()))
                .filter(order -> !hasText(safe.ragName()) || contains(order.ragName(), safe.ragName()))
                .filter(order -> !hasText(safe.knowledgeTag()) || contains(order.knowledgeTag(), safe.knowledgeTag()))
                .filter(order -> safe.status() == null || safe.status().equals(order.status()))
                .toList();
        if (!safe.paged()) {
            return filtered;
        }
        int start = (safe.normalizedPageNum() - 1) * safe.normalizedPageSize();
        int end = Math.min(start + safe.normalizedPageSize(), filtered.size());
        return start < filtered.size()
                ? List.copyOf(filtered.subList(start, end))
                : List.of();
    }

    private RagOrderDefinition requireDefinition(RagOrderDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("RAG_ORDER_DEFINITION_REQUIRED");
        }
        return definition;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private List<RagOrderDefinition> immutable(List<RagOrderDefinition> definitions) {
        return definitions == null || definitions.isEmpty()
                ? List.of()
                : List.copyOf(definitions);
    }

    private boolean contains(String value, String query) {
        return value != null && value.contains(query);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
