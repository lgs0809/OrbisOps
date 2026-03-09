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

class AiClientApiCatalogUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T03:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void createAppliesDefaultsTimesAndAuditsAfterPersistence() {
        AiClientApiCatalogPort port = mock(AiClientApiCatalogPort.class);
        AiClientApiCatalogAuditPort audit = mock(AiClientApiCatalogAuditPort.class);
        AiClientApiCatalogUseCase useCase = new AiClientApiCatalogUseCase(port, audit, CLOCK);
        AiClientApiDefinition requested = definition(
                null, "openai-main", "", "", "https://example.test/v1", "api-key", 1);
        when(port.insert(any())).thenReturn(true);

        assertTrue(useCase.create(requested));

        ArgumentCaptor<AiClientApiDefinition> captured = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        InOrder order = inOrder(port, audit);
        order.verify(port).insert(captured.capture());
        order.verify(audit).created(captured.getValue());
        AiClientApiDefinition saved = captured.getValue();
        assertEquals("openai-main", saved.providerName());
        assertEquals("OPENAI_COMPATIBLE", saved.providerType());
        assertEquals(LocalDateTime.of(2026, 7, 30, 3, 0), saved.createTime());
        assertEquals(saved.createTime(), saved.updateTime());
    }

    @Test
    void maskedUpdatePreservesExistingSecretAndDoesNotAuditFailedWrite() {
        AiClientApiCatalogPort port = mock(AiClientApiCatalogPort.class);
        AiClientApiCatalogAuditPort audit = mock(AiClientApiCatalogAuditPort.class);
        AiClientApiCatalogUseCase useCase = new AiClientApiCatalogUseCase(port, audit, CLOCK);
        AiClientApiDefinition before = definition(
                7L, "openai-main", "OpenAI", "OPENAI_COMPATIBLE",
                "https://old.test/v1", "stored-secret", 1);
        AiClientApiDefinition requested = definition(
                7L, "openai-main", "OpenAI", "OPENAI_COMPATIBLE",
                "https://new.test/v1", "******", 1);
        when(port.findById(7L)).thenReturn(before);
        when(port.updateById(any())).thenReturn(false);

        assertFalse(useCase.updateById(requested));

        ArgumentCaptor<AiClientApiDefinition> captured = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        verify(port).updateById(captured.capture());
        assertEquals("stored-secret", captured.getValue().apiKey());
        assertEquals(LocalDateTime.of(2026, 7, 30, 3, 0), captured.getValue().updateTime());
        verify(audit, never()).updatedById(any(), any(), any());
    }

    @Test
    void successfulDeleteReadsBeforeSnapshotThenAudits() {
        AiClientApiCatalogPort port = mock(AiClientApiCatalogPort.class);
        AiClientApiCatalogAuditPort audit = mock(AiClientApiCatalogAuditPort.class);
        AiClientApiCatalogUseCase useCase = new AiClientApiCatalogUseCase(port, audit, CLOCK);
        AiClientApiDefinition before = definition(
                7L, "openai-main", "OpenAI", "OPENAI_COMPATIBLE",
                "https://example.test/v1", "stored-secret", 1);
        when(port.findById(7L)).thenReturn(before);
        when(port.deleteById(7L)).thenReturn(true);

        assertTrue(useCase.deleteById(7L));

        InOrder order = inOrder(port, audit);
        order.verify(port).findById(7L);
        order.verify(port).deleteById(7L);
        order.verify(audit).deletedById(7L, before);
    }

    @Test
    void queryPreservesContainsFilteringAndPageDefaults() {
        AiClientApiCatalogPort port = mock(AiClientApiCatalogPort.class);
        AiClientApiCatalogAuditPort audit = mock(AiClientApiCatalogAuditPort.class);
        AiClientApiCatalogUseCase useCase = new AiClientApiCatalogUseCase(port, audit, CLOCK);
        when(port.listAll()).thenReturn(List.of(
                definition(1L, "openai-main", "OpenAI", "OPENAI_COMPATIBLE", "https://a.test/v1", "a", 1),
                definition(2L, "openai-backup", "OpenAI", "OPENAI_COMPATIBLE", "https://b.test/v1", "b", 0),
                definition(3L, "qwen-main", "Qwen", "OPENAI_COMPATIBLE", "https://a.test/v1", "c", 1)));

        List<AiClientApiDefinition> result = useCase.query(
                new AiClientApiCatalogQuery("openai", "test/v1", null, 2, 1));

        assertEquals(1, result.size());
        assertEquals("openai-backup", result.get(0).apiId());
        assertEquals(List.of(), useCase.query(
                new AiClientApiCatalogQuery("missing", null, null, 0, 0)));
    }

    private AiClientApiDefinition definition(
            Long id,
            String apiId,
            String providerName,
            String providerType,
            String baseUrl,
            String apiKey,
            Integer status) {
        return new AiClientApiDefinition(
                id,
                apiId,
                providerName,
                providerType,
                baseUrl,
                apiKey,
                "/chat/completions",
                "/embeddings",
                status,
                null,
                null);
    }
}
