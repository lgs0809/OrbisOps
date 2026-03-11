package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateSnapshot;
import cn.lgs.orbisops.application.rag.RagOrderCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsKnowledgeAggregateCatalogAdapterTest {

    @Test
    void mergesOrderCatalogAndKnowledgeStatisticsAndSortsByChunkCount() {
        RagOrderCatalogUseCase catalog = mock(RagOrderCatalogUseCase.class);
        IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
        OpsKnowledgeAggregateCatalogAdapter adapter = new OpsKnowledgeAggregateCatalogAdapter(catalog, repository);
        when(catalog.queryAll()).thenReturn(List.of(
                definition("rag-ops", "运维知识库", "ops", 1),
                definition("rag-default", "默认知识库", "", 0),
                definition("rag-biz", "业务知识库", "biz", 1)));
        when(repository.available()).thenReturn(true);
        when(repository.listKnowledgeStats()).thenReturn(List.of(
                Map.of("knowledgeTag", "ops", "chunkCount", 10L, "documentCount", 3L),
                Map.of("knowledgeTag", "biz", "chunkCount", 2L, "documentCount", 1L)));

        List<KnowledgeAggregateSnapshot> aggregates = adapter.listAggregates();

        assertEquals(List.of("ops", "biz", "default"), aggregates.stream()
                .map(KnowledgeAggregateSnapshot::knowledgeBaseId)
                .toList());
        assertEquals(10L, aggregates.get(0).chunkCount());
        assertEquals(3L, aggregates.get(0).documentCount());
        List<Map<String, Object>> opsOrders = aggregates.get(0).ragOrders();
        assertEquals(1, opsOrders.size());
        assertEquals("rag-ops", opsOrders.get(0).get("ragId"));
        assertEquals(0L, aggregates.get(2).chunkCount());
    }

    @Test
    void repositoryOrCatalogFailurePreservesLegacyEmptyFallback() {
        RagOrderCatalogUseCase catalog = mock(RagOrderCatalogUseCase.class);
        IRagKnowledgeRepository repository = mock(IRagKnowledgeRepository.class);
        OpsKnowledgeAggregateCatalogAdapter adapter = new OpsKnowledgeAggregateCatalogAdapter(catalog, repository);
        when(catalog.queryAll()).thenThrow(new IllegalStateException("database unavailable"));

        assertEquals(List.of(), adapter.listAggregates());
    }

    private RagOrderDefinition definition(
            String ragId,
            String ragName,
            String knowledgeTag,
            Integer status) {
        return new RagOrderDefinition(
                null,
                ragId,
                ragName,
                knowledgeTag,
                status,
                null,
                null);
    }
}
