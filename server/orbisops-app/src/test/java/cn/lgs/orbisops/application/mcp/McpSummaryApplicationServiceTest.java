package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpRuntimeCatalogRepository;
import cn.lgs.orbisops.domain.mcp.model.McpCatalogSummary;
import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSummaryApplicationServiceTest {

    private McpProjectDirectoryPort projects;
    private McpProjectToolCatalogPort catalog;
    private SelectRuntimeMcpToolsQuery runtimeTools;
    private IMcpRuntimeCatalogRepository runtimeCatalog;
    private McpJsonCodec jsonCodec;
    private McpSummaryApplicationService service;

    @BeforeEach
    void setUp() {
        projects = mock(McpProjectDirectoryPort.class);
        catalog = mock(McpProjectToolCatalogPort.class);
        runtimeTools = mock(SelectRuntimeMcpToolsQuery.class);
        runtimeCatalog = mock(IMcpRuntimeCatalogRepository.class);
        jsonCodec = new McpJsonCodec();
        service = new McpSummaryApplicationService(
                projects, catalog, runtimeTools, runtimeCatalog, jsonCodec,
                Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        when(projects.exists("project-1")).thenReturn(true);
        when(runtimeCatalog.available()).thenReturn(true);
    }

    @Test
    void summaryUsesEnabledProjectCatalogAndPersistsDeterministicProjection() {
        McpProjectToolDescriptor enabled = descriptor("logs-mcp", ProjectMcpStatus.ENABLED);
        McpProjectToolDescriptor disabled = descriptor("disabled-mcp", ProjectMcpStatus.DISABLED);
        when(catalog.list("project-1")).thenReturn(List.of(enabled, disabled));
        when(runtimeTools.runtimeExecutableTools("project-1", "logs-mcp", List.of(), List.of()))
                .thenReturn(List.of(Map.of("toolName", "search_logs")));

        Map<String, Object> result = service.summary("project-1");

        assertEquals(1, result.get("toolCount"));
        assertEquals("2026-07-20T00:00", result.get("generatedAt"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> tools =
                (List<Map<String, Object>>) result.get("tools");
        assertEquals(1, tools.get(0).get("runtimeExecutableToolCount"));
        assertEquals("search_logs",
                ((List<?>) tools.get(0).get("runtimeExecutableTools")).stream()
                        .map(Map.class::cast).findFirst().orElseThrow().get("toolName"));
        ArgumentCaptor<McpCatalogSummary> saved = ArgumentCaptor.forClass(McpCatalogSummary.class);
        verify(runtimeCatalog).saveCatalogSummary(saved.capture());
        assertEquals("project-1", saved.getValue().projectId());
        assertEquals(1, saved.getValue().toolCount());
        @SuppressWarnings("unchecked") Map<String, Object> persistedSummary =
                (Map<String, Object>) jsonCodec.decode(saved.getValue().summaryJson());
        assertEquals("project-1", persistedSummary.get("projectId"));
        assertEquals(1, ((Number) persistedSummary.get("toolCount")).intValue());
    }

    @Test
    void missingProjectAndUnavailableRuntimeStoreFailClosed() {
        when(projects.exists("missing")).thenReturn(false);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.summary("missing"));
        assertEquals("项目不存在：missing", missing.getMessage());

        when(runtimeCatalog.available()).thenReturn(false);
        IllegalStateException unavailable = assertThrows(IllegalStateException.class,
                () -> service.summary("project-1"));
        assertEquals("MCP_RUNTIME_CATALOG_STORE_UNAVAILABLE", unavailable.getMessage());
        verify(runtimeCatalog, never()).saveCatalogSummary(any());
    }

    @Test
    void rebuildUsesSameAuthoritativeProjection() {
        when(catalog.list("project-1")).thenReturn(List.of());

        Map<String, Object> result = service.rebuildSummary("project-1");

        assertEquals(0, result.get("toolCount"));
        verify(runtimeCatalog).saveCatalogSummary(any());
    }

    private McpProjectToolDescriptor descriptor(String mcpId, ProjectMcpStatus status) {
        return new McpProjectToolDescriptor(
                "project-1", mcpId, "Logs", "http", "stdio",
                List.of("READ"), McpRiskLevel.LOW, true, Map.of(), 30, status, Map.of());
    }
}
