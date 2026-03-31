package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.DiscoverMcpToolsProcessManager;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpAuthoritativeSchemaHydrationServiceTest {

    @Test
    void remoteSchemaComesFromProviderInspectionWithoutInvokingTool() {
        DiscoverMcpToolsProcessManager discovery = mock(DiscoverMcpToolsProcessManager.class);
        OpsProjectMcpRuntimeConfigService runtimeConfigs = mock(OpsProjectMcpRuntimeConfigService.class);
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        OpsMcpServerConfig config = OpsMcpServerConfig.builder()
                .projectId("demo-project")
                .mcpId("order-service-control-mcp")
                .build();
        McpHydratedToolSchema hydrated = mock(McpHydratedToolSchema.class);
        Map<String, Object> definition = Map.of(
                "name", "restart_service",
                "description", "Restart an allowlisted service",
                "inputSchema", Map.of(
                        "type", "object",
                        "required", List.of("service", "expectedVersion")));
        when(runtimeConfigs.resolveForDiscovery("demo-project", "order-service-control-mcp"))
                .thenReturn(Optional.of(config));
        when(toolProvider.inspectRemoteToolDefinition(config, "restart_service"))
                .thenReturn(definition);
        when(discovery.hydrateSchema(any())).thenReturn(hydrated);

        OpsMcpAuthoritativeSchemaHydrationService service =
                new OpsMcpAuthoritativeSchemaHydrationService(
                        discovery, runtimeConfigs, toolProvider,
                        new OpsMcpSchemaHydrationRequestMapper());

        assertEquals(hydrated, service.hydrate("demo-project", Map.of(
                "toolId", "order-service-control-mcp",
                "remoteToolName", "restart_service")));

        ArgumentCaptor<McpSchemaHydrationRequest> command =
                ArgumentCaptor.forClass(McpSchemaHydrationRequest.class);
        verify(discovery).hydrateSchema(command.capture());
        assertTrue(command.getValue().authoritativeDefinition().hydrated());
        assertEquals("Restart an allowlisted service",
                command.getValue().authoritativeDefinition().description());
        verify(toolProvider, never()).callProgressiveDirect(any(), anyString());
    }
}
