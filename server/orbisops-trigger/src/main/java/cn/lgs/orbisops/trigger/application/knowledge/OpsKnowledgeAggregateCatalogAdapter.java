package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateSnapshot;
import cn.lgs.orbisops.application.rag.RagOrderCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aggregate ACL combining legacy RAG order configuration and searchable RAG statistics. */
@Component
public class OpsKnowledgeAggregateCatalogAdapter implements KnowledgeAggregateCatalogPort {

    private final RagOrderCatalogUseCase ragOrderCatalog;
    private final IRagKnowledgeRepository repository;

    public OpsKnowledgeAggregateCatalogAdapter(
            RagOrderCatalogUseCase ragOrderCatalog,
            IRagKnowledgeRepository repository) {
        if (ragOrderCatalog == null) {
            throw new IllegalArgumentException("RAG_ORDER_CATALOG_USE_CASE_REQUIRED");
        }
        if (repository == null) {
            throw new IllegalArgumentException("RAG_KNOWLEDGE_REPOSITORY_REQUIRED");
        }
        this.ragOrderCatalog = ragOrderCatalog;
        this.repository = repository;
    }

    @Override
    public List<KnowledgeAggregateSnapshot> listAggregates() {
        try {
            return listAggregatesInternal();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<KnowledgeAggregateSnapshot> listAggregatesInternal() {
        Map<String, Aggregate> byTag = new HashMap<>();
        for (RagOrderDefinition order : ragOrderCatalog.queryAll()) {
            String tag = text(order.knowledgeTag(), "default");
            Aggregate aggregate = aggregate(byTag, tag);
            Map<String, Object> view = new HashMap<>();
            view.put("ragId", order.ragId());
            view.put("ragName", order.ragName());
            view.put("status", order.status());
            aggregate.ragOrders.add(Map.copyOf(view));
        }
        if (repository.available()) {
            List<Map<String, Object>> statistics = repository.listKnowledgeStats();
            for (Map<String, Object> statistic : statistics == null
                    ? List.<Map<String, Object>>of()
                    : statistics) {
                String tag = text(statistic.get("knowledgeTag"), "default");
                Aggregate aggregate = aggregate(byTag, tag);
                aggregate.chunkCount = number(statistic.get("chunkCount"));
                aggregate.documentCount = number(statistic.get("documentCount"));
            }
        }
        return byTag.values().stream()
                .map(Aggregate::snapshot)
                .sorted((left, right) -> Long.compare(right.chunkCount(), left.chunkCount()))
                .toList();
    }

    private Aggregate aggregate(Map<String, Aggregate> byTag, String tag) {
        return byTag.computeIfAbsent(tag, Aggregate::new);
    }

    private long number(Object value) {
        if (value instanceof Number number) return Math.max(0L, number.longValue());
        try {
            return value == null ? 0L : Math.max(0L, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static final class Aggregate {
        private final String knowledgeBaseId;
        private final List<Map<String, Object>> ragOrders = new ArrayList<>();
        private long documentCount;
        private long chunkCount;

        private Aggregate(String knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        private KnowledgeAggregateSnapshot snapshot() {
            return new KnowledgeAggregateSnapshot(
                    knowledgeBaseId, documentCount, chunkCount, ragOrders);
        }
    }
}
