package cn.lgs.orbisops.application.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagOrderCatalogUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createAndUpdateOwnTimestampsPersistenceAndAuditSequence() {
        RagOrderCatalogPort catalog = mock(RagOrderCatalogPort.class);
        RagOrderAuditPort audit = mock(RagOrderAuditPort.class);
        RagOrderCatalogUseCase useCase = new RagOrderCatalogUseCase(catalog, audit, CLOCK);
        RagOrderDefinition request = definition(7L, "rag-1", "运维知识库", "ops", 1);
        RagOrderDefinition before = new RagOrderDefinition(
                7L, "rag-1", "旧名称", "ops", 1,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 2, 0, 0));
        when(catalog.insert(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(catalog.queryById(7L)).thenReturn(before);
        when(catalog.updateById(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertTrue(useCase.create(request));
        assertTrue(useCase.updateById(request));

        ArgumentCaptor<RagOrderDefinition> insertCaptor = ArgumentCaptor.forClass(RagOrderDefinition.class);
        verify(catalog).insert(insertCaptor.capture());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), insertCaptor.getValue().createTime());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), insertCaptor.getValue().updateTime());

        ArgumentCaptor<RagOrderDefinition> updateCaptor = ArgumentCaptor.forClass(RagOrderDefinition.class);
        verify(catalog).updateById(updateCaptor.capture());
        assertEquals(null, updateCaptor.getValue().createTime());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), updateCaptor.getValue().updateTime());

        InOrder order = inOrder(catalog, audit);
        order.verify(catalog).insert(org.mockito.ArgumentMatchers.any());
        order.verify(audit).created(org.mockito.ArgumentMatchers.any());
        order.verify(catalog).queryById(7L);
        order.verify(catalog).updateById(org.mockito.ArgumentMatchers.any());
        order.verify(audit).updated(
                org.mockito.ArgumentMatchers.eq("update-by-id"),
                org.mockito.ArgumentMatchers.eq("7"),
                org.mockito.ArgumentMatchers.eq(before),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAuditsEffectiveOutcomeAfterPersistence() {
        RagOrderCatalogPort catalog = mock(RagOrderCatalogPort.class);
        RagOrderAuditPort audit = mock(RagOrderAuditPort.class);
        RagOrderCatalogUseCase useCase = new RagOrderCatalogUseCase(catalog, audit, CLOCK);
        RagOrderDefinition before = definition(7L, "rag-1", "运维知识库", "ops", 1);
        when(catalog.queryByRagId("rag-1")).thenReturn(before);
        when(catalog.deleteByRagId("rag-1")).thenReturn(false);

        assertFalse(useCase.deleteByRagId("rag-1"));

        InOrder order = inOrder(catalog, audit);
        order.verify(catalog).queryByRagId("rag-1");
        order.verify(catalog).deleteByRagId("rag-1");
        order.verify(audit).deleted("delete-by-rag-id", "rag-1", before, false);
    }

    @Test
    void queryListPreservesContainsFilteringAndLegacyPagination() {
        RagOrderCatalogPort catalog = mock(RagOrderCatalogPort.class);
        RagOrderAuditPort audit = mock(RagOrderAuditPort.class);
        RagOrderCatalogUseCase useCase = new RagOrderCatalogUseCase(catalog, audit, CLOCK);
        when(catalog.queryAll()).thenReturn(List.of(
                definition(1L, "rag-a", "运维知识库 A", "ops", 1),
                definition(2L, "rag-b", "运维知识库 B", "ops", 1),
                definition(3L, "rag-c", "业务知识库", "biz", 0)));

        List<RagOrderDefinition> firstPage = useCase.queryList(new RagOrderCatalogQuery(
                "rag-", "运维", "op", 1, 1, 1));
        List<RagOrderDefinition> secondPage = useCase.queryList(new RagOrderCatalogQuery(
                "rag-", "运维", "op", 1, 2, 1));

        assertEquals(List.of("rag-a"), firstPage.stream().map(RagOrderDefinition::ragId).toList());
        assertEquals(List.of("rag-b"), secondPage.stream().map(RagOrderDefinition::ragId).toList());
        assertEquals(List.of(), useCase.queryByStatus(null));
    }

    private RagOrderDefinition definition(
            Long id,
            String ragId,
            String ragName,
            String knowledgeTag,
            Integer status) {
        return new RagOrderDefinition(id, ragId, ragName, knowledgeTag, status, null, null);
    }
}
