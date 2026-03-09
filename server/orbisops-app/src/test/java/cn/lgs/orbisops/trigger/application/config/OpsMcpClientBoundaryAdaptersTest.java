package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpClientBoundaryAdaptersTest {

    @Test
    void catalogAdapterMapsTypedDefinitionAndRepositoryRecord() {
        IAiClientToolMcpConfigRepository repository = mock(IAiClientToolMcpConfigRepository.class);
        OpsMcpClientCatalogAdapter adapter = new OpsMcpClientCatalogAdapter(repository);
        when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        McpClientDefinition definition = definition("raw-config");

        assertTrue(adapter.insert(definition));

        ArgumentCaptor<AiClientConfigRecord> record = ArgumentCaptor.forClass(AiClientConfigRecord.class);
        verify(repository).insert(record.capture());
        assertEquals("ops-mcp", record.getValue().getMcpId());
        assertEquals("raw-config", record.getValue().getTransportConfig());
        assertEquals(definition.createTime(), record.getValue().getCreateTime());
    }

    @Test
    void catalogAdapterProjectsRepositoryLists() {
        IAiClientToolMcpConfigRepository repository = mock(IAiClientToolMcpConfigRepository.class);
        OpsMcpClientCatalogAdapter adapter = new OpsMcpClientCatalogAdapter(repository);
        when(repository.queryByStatus(1)).thenReturn(List.of(AiClientConfigRecord.builder()
                .id(7L)
                .mcpId("ops-mcp")
                .mcpName("Ops MCP")
                .transportType("stdio")
                .transportConfig("raw-config")
                .requestTimeout(30)
                .status(1)
                .build()));

        List<McpClientDefinition> result = adapter.listByStatus(1);

        assertEquals(1, result.size());
        assertEquals("ops-mcp", result.get(0).mcpId());
        assertEquals("raw-config", result.get(0).transportConfig());
    }

    @Test
    void transportProtectionRestoresPlaceholderAndMasksNestedJson() {
        OpsMcpTransportConfigProtectionAdapter adapter = new OpsMcpTransportConfigProtectionAdapter();
        String raw = "{\"url\":\"http://localhost\",\"apiKey\":\"value\",\"nested\":{\"token\":\"nested-value\"}}";

        assertEquals(raw, adapter.resolveIncoming("******", raw));
        assertEquals("******", adapter.resolveIncoming("******", null));
        assertEquals("{}", adapter.resolveIncoming("{}", raw));

        JSONObject protectedJson = JSON.parseObject(adapter.protectForRead(raw));
        assertEquals("http://localhost", protectedJson.getString("url"));
        assertEquals("******", protectedJson.getString("apiKey"));
        assertEquals("******", protectedJson.getJSONObject("nested").getString("token"));
    }

    @Test
    void transportProtectionMasksNonJsonFallback() {
        OpsMcpTransportConfigProtectionAdapter adapter = new OpsMcpTransportConfigProtectionAdapter();

        String protectedValue = adapter.protectForRead("token=plain-value,host=localhost");

        assertEquals("token=******,host=localhost", protectedValue);
    }

    @Test
    void runtimeCacheAdapterSupportsConfiguredAndMissingProvider() {
        OpsMcpToolProvider provider = mock(OpsMcpToolProvider.class);

        new OpsMcpClientRuntimeCacheAdapter(provider).invalidateAll();
        assertDoesNotThrow(() -> new OpsMcpClientRuntimeCacheAdapter(null).invalidateAll());

        verify(provider).invalidateAll();
    }

    @Test
    void auditAdapterPreservesCompatibilityActions() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsMcpClientAuditAdapter adapter = new OpsMcpClientAuditAdapter(audit);
        McpClientDefinition definition = definition("raw-config");

        adapter.created(definition);
        adapter.deletedByMcpId("ops-mcp", definition);

        verify(audit).record("mcp-config", "create", "ops-mcp", null, definition);
        verify(audit).record(
                "mcp-config",
                "delete-by-mcp-id",
                "ops-mcp",
                definition,
                Map.of("deleted", true));
    }

    private McpClientDefinition definition(String transportConfig) {
        return new McpClientDefinition(
                7L,
                "ops-mcp",
                "Ops MCP",
                "stdio",
                transportConfig,
                30,
                1,
                LocalDateTime.of(2026, 7, 30, 7, 0),
                LocalDateTime.of(2026, 7, 30, 7, 30));
    }
}
