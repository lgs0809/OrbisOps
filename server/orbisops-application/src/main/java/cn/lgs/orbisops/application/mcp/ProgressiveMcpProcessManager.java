package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Map;

public final class ProgressiveMcpProcessManager {

    private final McpSummaryPort summaries;
    private final McpDiscoveryPort discovery;
    private final McpRuntimeOperationsPort runtime;

    public ProgressiveMcpProcessManager(McpSummaryPort summaries,
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
        return discovery.select(required(command, "MCP_DISCOVERY_COMMAND_REQUIRED"));
    }

    public McpHydratedToolSchema hydrateSchema(McpSchemaHydrationRequest command) {
        return discovery.hydrateSchema(required(command, "MCP_HYDRATE_COMMAND_REQUIRED"));
    }

    public McpRuntimeActivationResult activateRuntimeTool(McpRuntimeActivationRequest command) {
        return runtime.activateRuntimeTool(required(command, "MCP_ACTIVATION_COMMAND_REQUIRED"));
    }

    public List<Map<String, Object>> runtimeCatalog(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools) {
        return runtime.runtimeCatalog(required(projectId, "MCP_PROJECT_ID_REQUIRED"),
                required(toolIdOrMcpId, "MCP_TOOL_ID_REQUIRED"),
                allowedTools == null ? List.of() : List.copyOf(allowedTools),
                blockedTools == null ? List.of() : List.copyOf(blockedTools));
    }

    public List<Map<String, Object>> runtimeCatalog(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools,
                                                     String executionStage) {
        return runtime.runtimeCatalog(required(projectId, "MCP_PROJECT_ID_REQUIRED"),
                required(toolIdOrMcpId, "MCP_TOOL_ID_REQUIRED"),
                allowedTools == null ? List.of() : List.copyOf(allowedTools),
                blockedTools == null ? List.of() : List.copyOf(blockedTools),
                required(executionStage, "MCP_EXECUTION_STAGE_REQUIRED"));
    }

    public List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                             String toolIdOrMcpId,
                                                             List<String> allowedTools,
                                                             List<String> blockedTools) {
        return runtime.runtimeExecutableTools(required(projectId, "MCP_PROJECT_ID_REQUIRED"),
                required(toolIdOrMcpId, "MCP_TOOL_ID_REQUIRED"),
                allowedTools == null ? List.of() : List.copyOf(allowedTools),
                blockedTools == null ? List.of() : List.copyOf(blockedTools));
    }

    public List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                             String toolIdOrMcpId,
                                                             List<String> allowedTools,
                                                             List<String> blockedTools,
                                                             String executionStage) {
        return runtime.runtimeExecutableTools(required(projectId, "MCP_PROJECT_ID_REQUIRED"),
                required(toolIdOrMcpId, "MCP_TOOL_ID_REQUIRED"),
                allowedTools == null ? List.of() : List.copyOf(allowedTools),
                blockedTools == null ? List.of() : List.copyOf(blockedTools),
                required(executionStage, "MCP_EXECUTION_STAGE_REQUIRED"));
    }

    public boolean isRuntimeToolActivated(McpCommands.RuntimeActivationCheck command) {
        return runtime.isRuntimeToolActivated(required(command, "MCP_ACTIVATION_CHECK_REQUIRED"));
    }

    public boolean requiresRuntimeActivation(McpHydratedToolSchema schema) {
        return runtime.requiresRuntimeActivation(required(schema, "MCP_HYDRATED_SCHEMA_REQUIRED"));
    }

    public void recordMcpCall(McpCommands.RuntimeCall command) {
        runtime.recordMcpCall(required(command, "MCP_RUNTIME_CALL_REQUIRED"));
    }

    public void recordToolRoutingWarning(McpCommands.RoutingWarning command) {
        runtime.recordToolRoutingWarning(required(command, "MCP_ROUTING_WARNING_REQUIRED"));
    }

    private <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
