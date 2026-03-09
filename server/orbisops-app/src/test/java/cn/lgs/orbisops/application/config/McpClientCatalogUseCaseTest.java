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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpClientCatalogUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T07:10:00Z"),
            ZoneOffset.UTC);

    @Test
    void createPersistsThenInvalidatesRuntimeThenAudits() {
        McpClientCatalogPort catalog = mock(McpClientCatalogPort.class);
        McpClientRuntimeCachePort cache = mock(McpClientRuntimeCachePort.class);
        McpClientAuditPort audit = mock(McpClientAuditPort.class);
        McpTransportConfigProtectionPort protection = mock(McpTransportConfigProtectionPort.class);
        McpClientCatalogUseCase useCase = new McpClientCatalogUseCase(
                catalog, cache, audit, protection, CLOCK);
        when(protection.resolveIncoming("{}", null)).thenReturn("{}");
        when(catalog.insert(any())).thenReturn(true);

        assertTrue(useCase.create(command(null, "ops-mcp", "{}")));

        ArgumentCaptor<McpClientDefinition> saved = ArgumentCaptor.forClass(McpClientDefinition.class);
        InOrder order = inOrder(catalog, cache, audit);
        order.verify(catalog).insert(saved.capture());
        order.verify(cache).invalidateAll();
        order.verify(audit).created(saved.getValue());
        assertEquals(LocalDateTime.of(2026, 7, 30, 7, 10), saved.getValue().createTime());
        assertEquals(saved.getValue().createTime(), saved.getValue().updateTime());
    }

    @Test
    void updateMissDoesNotInvalidateOrAudit() {
        McpClientCatalogPort catalog = mock(McpClientCatalogPort.class);
        McpClientRuntimeCachePort cache = mock(McpClientRuntimeCachePort.class);
        McpClientAuditPort audit = mock(McpClientAuditPort.class);
        McpTransportConfigProtectionPort protection = mock(McpTransportConfigProtectionPort.class);
        McpClientCatalogUseCase useCase = new McpClientCatalogUseCase(
                catalog, cache, audit, protection, CLOCK);
        when(protection.resolveIncoming("{}", null)).thenReturn("{}");
        when(catalog.updateById(any())).thenReturn(false);

        assertFalse(useCase.updateById(command(404L, "missing-mcp", "{}")));

        verify(cache, never()).invalidateAll();
        verify(audit, never()).updatedById(any(), any(), any());
    }

    @Test
    void updateRestoresPlaceholderFromExistingConfiguration() {
        McpClientCatalogPort catalog = mock(McpClientCatalogPort.class);
        McpClientRuntimeCachePort cache = mock(McpClientRuntimeCachePort.class);
        McpClientAuditPort audit = mock(McpClientAuditPort.class);
        McpTransportConfigProtectionPort protection = mock(McpTransportConfigProtectionPort.class);
        McpClientCatalogUseCase useCase = new McpClientCatalogUseCase(
                catalog, cache, audit, protection, CLOCK);
        McpClientDefinition before = definition(7L, "ops-mcp", "raw-config");
        when(catalog.findById(7L)).thenReturn(before);
        when(protection.resolveIncoming("masked-config", "raw-config")).thenReturn("raw-config");
        when(catalog.updateById(any())).thenReturn(true);

        assertTrue(useCase.updateById(command(7L, "ops-mcp", "masked-config")));

        ArgumentCaptor<McpClientDefinition> updated = ArgumentCaptor.forClass(McpClientDefinition.class);
        verify(catalog).updateById(updated.capture());
        assertEquals("raw-config", updated.getValue().transportConfig());
        assertEquals(before.createTime(), updated.getValue().createTime());
        verify(cache).invalidateAll();
        verify(audit).updatedById(7L, before, updated.getValue());
    }

    @Test
    void runtimeInvalidationFailureStopsAuditAndPropagates() {
        McpClientCatalogPort catalog = mock(McpClientCatalogPort.class);
        McpClientRuntimeCachePort cache = mock(McpClientRuntimeCachePort.class);
        McpClientAuditPort audit = mock(McpClientAuditPort.class);
        McpTransportConfigProtectionPort protection = mock(McpTransportConfigProtectionPort.class);
        McpClientCatalogUseCase useCase = new McpClientCatalogUseCase(
                catalog, cache, audit, protection, CLOCK);
        when(protection.resolveIncoming("{}", null)).thenReturn("{}");
        when(catalog.insert(any())).thenReturn(true);
        doThrow(new IllegalStateException("cache unavailable")).when(cache).invalidateAll();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> useCase.create(command(null, "ops-mcp", "{}")));

        assertEquals("cache unavailable", error.getMessage());
        verify(audit, never()).created(any());
    }

    @Test
    void queryPreservesSelectorPriorityThenNameFilterAndProtection() {
        McpClientCatalogPort catalog = mock(McpClientCatalogPort.class);
        McpTransportConfigProtectionPort protection = mock(McpTransportConfigProtectionPort.class);
        McpClientCatalogUseCase useCase = new McpClientCatalogUseCase(
                catalog,
                mock(McpClientRuntimeCachePort.class),
                mock(McpClientAuditPort.class),
                protection,
                CLOCK);
        McpClientDefinition selected = definition(7L, "ops-mcp", "raw-config");
        when(catalog.findByMcpId("ops-mcp")).thenReturn(selected);
        when(protection.protectForRead("raw-config")).thenReturn("protected-config");

        List<McpClientDefinition> result = useCase.query(new McpClientCatalogQuery(
                "ops-mcp", "Ops", "stdio", 1));

        assertEquals(1, result.size());
        assertEquals("protected-config", result.get(0).transportConfig());
        verify(catalog).findByMcpId("ops-mcp");
        verify(catalog, never()).listByStatus(any());
        verify(catalog, never()).listByTransportType(any());
        verify(catalog, never()).listAll();
    }

    private McpClientCommand command(Long id, String mcpId, String transportConfig) {
        return new McpClientCommand(
                id,
                mcpId,
                "Ops MCP",
                "stdio",
                transportConfig,
                30,
                1);
    }

    private McpClientDefinition definition(Long id, String mcpId, String transportConfig) {
        return new McpClientDefinition(
                id,
                mcpId,
                "Ops MCP",
                "stdio",
                transportConfig,
                30,
                1,
                LocalDateTime.of(2026, 7, 29, 7, 0),
                LocalDateTime.of(2026, 7, 29, 7, 30));
    }
}
