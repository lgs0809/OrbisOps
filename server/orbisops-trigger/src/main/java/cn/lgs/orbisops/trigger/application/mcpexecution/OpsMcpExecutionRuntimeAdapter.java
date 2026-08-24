package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpHydratedToolSchema;
import cn.lgs.orbisops.application.mcp.McpRuntimeActivationRequest;
import cn.lgs.orbisops.application.mcp.McpRuntimeActivationResult;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRuntimePort;
import cn.lgs.orbisops.application.mcpexecution.McpRuntimeActivation;
import cn.lgs.orbisops.application.mcpexecution.McpRuntimeCatalog;
import cn.lgs.orbisops.application.mcpexecution.McpRuntimeToolSchema;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsMcpExecutionRuntimeAdapter implements McpExecutionRuntimePort {

    private final ProgressiveMcpProcessManager runtime;

    public OpsMcpExecutionRuntimeAdapter(ProgressiveMcpProcessManager runtime) {
        this.runtime = runtime;
    }

    @Override
    public McpRuntimeCatalog catalog(McpExecutionConfig config) {
        return new McpRuntimeCatalog(runtime.runtimeCatalog(
                config.projectId(),
                config.toolId(),
                config.allowedTools(),
                config.blockedTools(),
                config.landingApproved() ? "LANDING" : "PREPARE"));
    }

    @Override
    public McpRuntimeActivation activate(
            McpExecutionRequest request,
            String toolName,
            String reason,
            Map<String, Object> remoteDefinition) {
        McpExecutionConfig config = request.config();
        McpRuntimeActivationResult result = runtime.activateRuntimeTool(
                new McpRuntimeActivationRequest(
                config.projectId(),
                config.runId(),
                request.actor(),
                config.mcpId(),
                toolName,
                "",
                config.agentId(),
                request.actor(),
                reason,
                request.trustedLandingRuntime()
                        ? "LANDING"
                        : "PRE_APPROVAL_WORKFLOW",
                request.trustedLandingRuntime(),
                authoritativeDefinition(remoteDefinition)));
        Map<String, Object> view = result.view();
        return new McpRuntimeActivation(text(view.get("status")), view);
    }

    @Override
    public McpRuntimeToolSchema schema(McpExecutionRequest request, String toolName) {
        return schemaResult(runtime.hydrateSchema(hydrationRequest(
                request, toolName, Map.of())));
    }

    @Override
    public McpRuntimeToolSchema hydrate(
            McpExecutionRequest request,
            String toolName,
            Map<String, Object> remoteDefinition) {
        return schemaResult(runtime.hydrateSchema(hydrationRequest(
                request, toolName, remoteDefinition)));
    }

    @Override
    public boolean activated(McpExecutionConfig config, String toolName) {
        return runtime.isRuntimeToolActivated(new McpCommands.RuntimeActivationCheck(
                config.projectId(), config.runId(), config.mcpId(), toolName));
    }

    @Override
    public void recordCall(McpRuntimeCallEvent event) {
        runtime.recordMcpCall(new McpCommands.RuntimeCall(
                event.projectId(), event.agentId(), event.nodeId(), event.runId(),
                event.toolId(), event.mcpId(), event.toolName(), event.status(),
                event.inputSummary(), event.output(), event.durationMs(), event.metadata()));
    }

    private McpSchemaHydrationRequest hydrationRequest(
            McpExecutionRequest request,
            String toolName,
            Map<String, Object> remoteDefinition) {
        McpExecutionConfig config = request.config();
        return new McpSchemaHydrationRequest(
                config.projectId(),
                config.toolId(),
                toolName,
                config.agentId(),
                request.actor(),
                authoritativeDefinition(remoteDefinition));
    }

    private McpRuntimeToolSchema schemaResult(McpHydratedToolSchema schema) {
        Map<String, Object> attributes = schema == null ? Map.of() : schema.view();
        return new McpRuntimeToolSchema(
                attributes,
                schema != null && runtime.requiresRuntimeActivation(schema));
    }

    private McpAuthoritativeToolDefinition authoritativeDefinition(
            Map<String, Object> remoteDefinition) {
        Map<String, Object> safe = remoteDefinition == null ? Map.of() : remoteDefinition;
        Object schema = safe.containsKey("inputSchema")
                ? safe.get("inputSchema")
                : safe.get("schema");
        return new McpAuthoritativeToolDefinition(
                text(safe.get("description")),
                schema,
                safe.get("outputSchema"),
                "REMOTE_MCP_TOOL_DEFINITION");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
