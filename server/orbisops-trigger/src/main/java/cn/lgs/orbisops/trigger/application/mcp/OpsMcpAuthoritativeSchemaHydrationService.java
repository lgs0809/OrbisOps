package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.DiscoverMcpToolsProcessManager;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Hydrates an MCP tool schema from the provider's authoritative tools/list
 * definition. Inspection is read-only and never invokes the remote tool.
 */
@Service
public final class OpsMcpAuthoritativeSchemaHydrationService {

    private final DiscoverMcpToolsProcessManager discovery;
    private final OpsProjectMcpRuntimeConfigService runtimeConfigs;
    private final OpsMcpToolProvider toolProvider;
    private final OpsMcpSchemaHydrationRequestMapper mapper;

    public OpsMcpAuthoritativeSchemaHydrationService(
            DiscoverMcpToolsProcessManager discovery,
            OpsProjectMcpRuntimeConfigService runtimeConfigs,
            OpsMcpToolProvider toolProvider,
            OpsMcpSchemaHydrationRequestMapper mapper) {
        if (discovery == null) throw new IllegalArgumentException("MCP_DISCOVERY_REQUIRED");
        if (runtimeConfigs == null) throw new IllegalArgumentException("MCP_RUNTIME_CONFIGS_REQUIRED");
        if (toolProvider == null) throw new IllegalArgumentException("MCP_TOOL_PROVIDER_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("MCP_SCHEMA_MAPPER_REQUIRED");
        this.discovery = discovery;
        this.runtimeConfigs = runtimeConfigs;
        this.toolProvider = toolProvider;
        this.mapper = mapper;
    }

    public McpHydratedToolSchema hydrate(String projectId, Map<String, Object> request) {
        McpSchemaHydrationRequest requested = mapper.hydration(projectId, request);
        if (!requested.remoteToolRequested()) {
            return discovery.hydrateSchema(requested);
        }

        OpsMcpServerConfig config = runtimeConfigs
                .resolveForDiscovery(projectId, requested.toolId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "项目未配置可发现 MCP：" + requested.toolId()));
        Map<String, Object> authoritativeDefinition =
                toolProvider.inspectRemoteToolDefinition(config, requested.remoteToolName());
        return discovery.hydrateSchema(
                mapper.hydration(projectId, request, authoritativeDefinition));
    }
}
