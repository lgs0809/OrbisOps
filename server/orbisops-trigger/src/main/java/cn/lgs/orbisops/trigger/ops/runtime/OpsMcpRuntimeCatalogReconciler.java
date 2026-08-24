package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Lazily reconciles platform-generated MCP server definitions into the reviewed runtime catalog.
 * External/user supplied MCPs keep their explicit import/review lifecycle and are never auto-promoted here.
 */
@Service
public final class OpsMcpRuntimeCatalogReconciler {

    private final OpsMcpToolProvider toolProvider;
    private final ObjectProvider<ProgressiveMcpProcessManager> processManagerProvider;

    public OpsMcpRuntimeCatalogReconciler(
            OpsMcpToolProvider toolProvider,
            ObjectProvider<ProgressiveMcpProcessManager> processManagerProvider) {
        if (toolProvider == null) throw new IllegalArgumentException("MCP_TOOL_PROVIDER_REQUIRED");
        if (processManagerProvider == null) {
            throw new IllegalArgumentException("PROGRESSIVE_MCP_PROCESS_MANAGER_PROVIDER_REQUIRED");
        }
        this.toolProvider = toolProvider;
        this.processManagerProvider = processManagerProvider;
    }

    public void reconcileIfNeeded(OpsMcpServerConfig config) {
        if (!platformGenerated(config)) return;
        ProgressiveMcpProcessManager manager = processManagerProvider.getIfAvailable();
        if (manager == null) return;
        String projectId = text(config.getProjectId());
        String mcpId = firstText(config.getToolId(), config.getMcpId(), config.getName());
        if (projectId.isBlank() || mcpId.isBlank()) return;
        String stage = switch (OpsRuntimeAgentAuthority.valueOf(
                firstText(text(config.getRuntimeAuthority()), OpsRuntimeAgentAuthority.OBSERVE_ONLY.name()))) {
            case OBSERVE_ONLY -> AgentExecutionStage.INVESTIGATE.name();
            case PREPARE_CHANGE -> AgentExecutionStage.PREPARE.name();
            case PROD_FULL -> AgentExecutionStage.LANDING.name();
        };
        List<Map<String, Object>> executable = manager.runtimeExecutableTools(
                projectId, mcpId, config.getAllowedTools(), config.getBlockedTools(), stage);
        if (executable != null && !executable.isEmpty()) return;

        for (Map<String, Object> definition : toolProvider.inspectRemoteToolDefinitions(config)) {
            String toolName = text(definition.get("toolName"));
            Object inputSchema = definition.containsKey("inputSchema")
                    ? definition.get("inputSchema") : definition.get("schema");
            if (toolName.isBlank() || inputSchema == null) continue;
            manager.hydrateSchema(new McpSchemaHydrationRequest(
                    projectId,
                    firstText(config.getMcpId(), mcpId),
                    toolName,
                    text(config.getAgentId()),
                    "",
                    new McpAuthoritativeToolDefinition(
                            text(definition.get("description")),
                            inputSchema,
                            definition.get("outputSchema"),
                            firstText(text(definition.get("schemaSource")), "REMOTE_MCP_TOOL_DEFINITION"))));
        }
    }

    private boolean platformGenerated(OpsMcpServerConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getProgressiveManaged())) return false;
        Map<String, String> capabilities = config.getToolCapabilities();
        return capabilities != null
                && "true".equalsIgnoreCase(text(capabilities.get("platformGenerated")));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
