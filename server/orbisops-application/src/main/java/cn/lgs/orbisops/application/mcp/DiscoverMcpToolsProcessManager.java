package cn.lgs.orbisops.application.mcp;

import java.util.Map;

public final class DiscoverMcpToolsProcessManager {

    private final McpSummaryPort summaries;
    private final McpDiscoveryPort discovery;
    private final McpRuntimeOperationsPort runtime;

    public DiscoverMcpToolsProcessManager(McpSummaryPort summaries,
                                          McpDiscoveryPort discovery,
                                          McpRuntimeOperationsPort runtime) {
        if (summaries == null) throw new IllegalArgumentException("MCP_SUMMARY_PORT_REQUIRED");
        if (discovery == null) throw new IllegalArgumentException("MCP_DISCOVERY_PORT_REQUIRED");
        if (runtime == null) throw new IllegalArgumentException("MCP_RUNTIME_OPERATIONS_PORT_REQUIRED");
        this.summaries = summaries;
        this.discovery = discovery;
        this.runtime = runtime;
    }

    public Map<String, Object> summary(String projectId) {
        return summaries.summary(required(projectId, "MCP_PROJECT_ID_REQUIRED"));
    }

    public Map<String, Object> rebuildSummary(String projectId) {
        return summaries.rebuildSummary(required(projectId, "MCP_PROJECT_ID_REQUIRED"));
    }

    public McpDiscoverySelectionResult select(McpDiscoverySelectionRequest command) {
        if (command == null) throw new IllegalArgumentException("MCP_DISCOVERY_COMMAND_REQUIRED");
        return discovery.select(command);
    }

    public McpHydratedToolSchema hydrateSchema(McpSchemaHydrationRequest command) {
        if (command == null) throw new IllegalArgumentException("MCP_HYDRATE_COMMAND_REQUIRED");
        return discovery.hydrateSchema(command);
    }

    public McpRuntimeActivationResult activateRuntimeTool(McpRuntimeActivationRequest command) {
        if (command == null) throw new IllegalArgumentException("MCP_ACTIVATION_COMMAND_REQUIRED");
        return runtime.activateRuntimeTool(command);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
