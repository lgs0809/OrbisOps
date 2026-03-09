package cn.lgs.orbisops.application.config;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientModelCatalogUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T05:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void createAppliesDescriptionDefaultAndTimesBeforeAudit() {
        AiClientModelCatalogPort port = mock(AiClientModelCatalogPort.class);
        AiClientModelCatalogAuditPort audit = mock(AiClientModelCatalogAuditPort.class);
        AiClientModelCatalogUseCase useCase = new AiClientModelCatalogUseCase(port, audit, CLOCK);
        when(port.insert(any())).thenReturn(true);

        assertTrue(useCase.create(definition(null, "gpt-main", "openai-main", null, 1)));

        ArgumentCaptor<AiClientModelDefinition> saved = ArgumentCaptor.forClass(AiClientModelDefinition.class);
        InOrder order = inOrder(port, audit);
        order.verify(port).insert(saved.capture());
        order.verify(audit).created(saved.getValue());
        assertEquals("", saved.getValue().description());
        assertEquals(LocalDateTime.of(2026, 7, 30, 5, 0), saved.getValue().createTime());
        assertEquals(saved.getValue().createTime(), saved.getValue().updateTime());
    }

    @Test
    void failedUpdateKeepsTypedNormalizationButDoesNotAudit() {
        AiClientModelCatalogPort port = mock(AiClientModelCatalogPort.class);
        AiClientModelCatalogAuditPort audit = mock(AiClientModelCatalogAuditPort.class);
        AiClientModelCatalogUseCase useCase = new AiClientModelCatalogUseCase(port, audit, CLOCK);
        AiClientModelDefinition before = definition(7L, "gpt-main", "openai-main", "old", 1);
        AiClientModelDefinition requested = definition(7L, "gpt-main", "openai-main", "", 0);
        when(port.findById(7L)).thenReturn(before);
        when(port.updateById(any())).thenReturn(false);

        assertFalse(useCase.updateById(requested));

        ArgumentCaptor<AiClientModelDefinition> updated = ArgumentCaptor.forClass(AiClientModelDefinition.class);
        verify(port).updateById(updated.capture());
        assertEquals("", updated.getValue().description());
        assertEquals(LocalDateTime.of(2026, 7, 30, 5, 0), updated.getValue().updateTime());
        verify(audit, never()).updatedById(any(), any(), any());
    }

    @Test
    void successfulDeleteReadsBeforeThenPersistsThenAudits() {
        AiClientModelCatalogPort port = mock(AiClientModelCatalogPort.class);
        AiClientModelCatalogAuditPort audit = mock(AiClientModelCatalogAuditPort.class);
        AiClientModelCatalogUseCase useCase = new AiClientModelCatalogUseCase(port, audit, CLOCK);
        AiClientModelDefinition before = definition(7L, "gpt-main", "openai-main", "old", 1);
        when(port.findByModelId("gpt-main")).thenReturn(before);
        when(port.deleteByModelId("gpt-main")).thenReturn(true);

        assertTrue(useCase.deleteByModelId("gpt-main"));

        InOrder order = inOrder(port, audit);
        order.verify(port).findByModelId("gpt-main");
        order.verify(port).deleteByModelId("gpt-main");
        order.verify(audit).deletedByModelId("gpt-main", before);
    }

    @Test
    void queryPreservesLegacySelectorPriority() {
        AiClientModelCatalogPort port = mock(AiClientModelCatalogPort.class);
        AiClientModelCatalogAuditPort audit = mock(AiClientModelCatalogAuditPort.class);
        AiClientModelCatalogUseCase useCase = new AiClientModelCatalogUseCase(port, audit, CLOCK);
        AiClientModelDefinition selected = definition(7L, "gpt-main", "openai-main", "main", 1);
        when(port.findByModelId("gpt-main")).thenReturn(selected);

        List<AiClientModelDefinition> result = useCase.query(new AiClientModelCatalogQuery(
                "gpt-main", "ignored-api", "ignored-type", 1));

        assertEquals(List.of(selected), result);
        verify(port).findByModelId("gpt-main");
        verify(port, never()).findByApiId(any());
        verify(port, never()).findByModelType(any());
        verify(port, never()).listEnabled();
        verify(port, never()).listAll();
    }

    @Test
    void queryFallsThroughApiTypeEnabledAndAllBranches() {
        AiClientModelCatalogPort port = mock(AiClientModelCatalogPort.class);
        AiClientModelCatalogAuditPort audit = mock(AiClientModelCatalogAuditPort.class);
        AiClientModelCatalogUseCase useCase = new AiClientModelCatalogUseCase(port, audit, CLOCK);
        AiClientModelDefinition value = definition(7L, "gpt-main", "openai-main", "main", 1);
        when(port.findByApiId("openai-main")).thenReturn(List.of(value));
        when(port.findByModelType("CHAT")).thenReturn(List.of(value));
        when(port.listEnabled()).thenReturn(List.of(value));
        when(port.listAll()).thenReturn(List.of(value));

        assertEquals(List.of(value), useCase.query(new AiClientModelCatalogQuery(null, "openai-main", "CHAT", 1)));
        assertEquals(List.of(value), useCase.query(new AiClientModelCatalogQuery(null, null, "CHAT", 1)));
        assertEquals(List.of(value), useCase.query(new AiClientModelCatalogQuery(null, null, null, 1)));
        assertEquals(List.of(value), useCase.query(AiClientModelCatalogQuery.all()));
    }

    private AiClientModelDefinition definition(
            Long id,
            String modelId,
            String apiId,
            String description,
            Integer status) {
        return new AiClientModelDefinition(
                id,
                modelId,
                apiId,
                modelId,
                "CHAT",
                "CHAT",
                description,
                status,
                null,
                null);
    }
}
