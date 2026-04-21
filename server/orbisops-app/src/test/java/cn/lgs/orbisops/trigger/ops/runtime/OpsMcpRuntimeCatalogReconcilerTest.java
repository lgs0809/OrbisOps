package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMcpRuntimeCatalogReconcilerTest {

    @Test
    void hydratesRealRemoteDefinitionsForPlatformGeneratedMcpWhenRuntimeCatalogIsEmpty() {
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveMcpProcessManager> provider = mock(ObjectProvider.class);
        ProgressiveMcpProcessManager manager = mock(ProgressiveMcpProcessManager.class);
        when(provider.getIfAvailable()).thenReturn(manager);
        when(manager.runtimeExecutableTools(
                eq("demo-project"), eq("mysql-mcp"), anyList(), anyList(), eq("INVESTIGATE")))
                .thenReturn(List.of());
        when(toolProvider.inspectRemoteToolDefinitions(any())).thenReturn(List.of(Map.of(
                "toolName", "mysql_health",
                "description", "health",
                "inputSchema", Map.of("type", "object"),
                "schemaSource", "REMOTE_MCP_TOOL_DEFINITION")));
        when(manager.hydrateSchema(any(McpSchemaHydrationRequest.class)))
                .thenReturn(mock(McpHydratedToolSchema.class));

        new OpsMcpRuntimeCatalogReconciler(toolProvider, provider).reconcileIfNeeded(platformGenerated());

        verify(toolProvider).inspectRemoteToolDefinitions(any());
        verify(manager).hydrateSchema(any(McpSchemaHydrationRequest.class));
    }

    @Test
    void leavesExternalOrUserManagedMcpOnExplicitReviewLifecycle() {
        OpsMcpToolProvider toolProvider = mock(OpsMcpToolProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveMcpProcessManager> provider = mock(ObjectProvider.class);
        ProgressiveMcpProcessManager manager = mock(ProgressiveMcpProcessManager.class);
        when(provider.getIfAvailable()).thenReturn(manager);

        OpsMcpServerConfig external = platformGenerated();
        external.setToolCapabilities(Map.of(
                "platformGenerated", "false",
                "readOnly", "true",
                "resourceEnvironment", "external",
                "allowedStages", "INVESTIGATE"));

        new OpsMcpRuntimeCatalogReconciler(toolProvider, provider).reconcileIfNeeded(external);

        verify(manager, never()).runtimeExecutableTools(any(), any(), anyList(), anyList(), any());
        verify(toolProvider, never()).inspectRemoteToolDefinitions(any());
    }

    private OpsMcpServerConfig platformGenerated() {
        return OpsMcpServerConfig.builder()
                .name("mysql-mcp")
                .projectId("demo-project")
                .mcpId("mysql-mcp")
                .toolId("mysql-mcp")
                .progressiveManaged(true)
                .runtimeAuthority("OBSERVE_ONLY")
                .allowedTools(List.of("mysql_health"))
                .blockedTools(List.of())
                .toolCapabilities(Map.of(
                        "platformGenerated", "true",
                        "readOnly", "true",
                        "resourceEnvironment", "prod",
                        "allowedStages", "INVESTIGATE,PREPARE,LANDING"))
                .build();
    }
}
