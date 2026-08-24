package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpAuthoritativeToolDefinition;
import cn.lgs.orbisops.application.mcp.McpCommands;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionRequest;
import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionResult;
import cn.lgs.orbisops.application.mcp.McpSchemaHydrationRequest;
import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Owns progressive MCP exposure, routing, schema hydration and pre-remote-call audit. */
@Slf4j
public final class OpsMcpProgressiveRuntimeAdapter {

    private final Supplier<ProgressiveMcpProcessManager> processManagerSupplier;
    private final OpsMcpProgressiveSettings settings;
    private final OpsMcpRemoteCallPolicy remoteCallPolicy;

    public OpsMcpProgressiveRuntimeAdapter(
            Supplier<ProgressiveMcpProcessManager> processManagerSupplier,
            OpsMcpProgressiveSettings settings,
            OpsMcpRemoteCallPolicy remoteCallPolicy) {
        if (processManagerSupplier == null) {
            throw new IllegalArgumentException("PROGRESSIVE_MCP_PROCESS_MANAGER_SUPPLIER_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("PROGRESSIVE_MCP_SETTINGS_REQUIRED");
        }
        if (remoteCallPolicy == null) {
            throw new IllegalArgumentException("MCP_REMOTE_CALL_POLICY_REQUIRED");
        }
        this.processManagerSupplier = processManagerSupplier;
        this.settings = settings;
        this.remoteCallPolicy = remoteCallPolicy;
    }

    public OpsMcpProgressiveExposure exposure(OpsMcpServerConfig config) {
        ProgressiveMcpProcessManager processManager = processManagerSupplier.get();
        if (progressiveCandidate(config, processManager)) {
            String projectId = value(config.getProjectId());
            String mcpId = value(config.getMcpId(), config.getName());
            processManager.summary(projectId);
            List<Map<String, Object>> runtimeTools = processManager.runtimeExecutableTools(
                    projectId,
                    value(config.getToolId(), mcpId),
                    config.getAllowedTools(),
                    config.getBlockedTools(),
                    catalogStage(config));
            if (runtimeTools == null || runtimeTools.isEmpty()) {
                processManager.recordToolRoutingWarning(routingWarning(
                        config,
                        "NO_ACTIVE_RUNTIME_MCP_TOOL"));
                return OpsMcpProgressiveExposure.denied();
            }
            return OpsMcpProgressiveExposure.managed(
                    runtimeTools,
                    settings.disclosureEnabled());
        }
        if (settings.enforceProjectManaged()
                && config != null
                && StringUtils.hasText(config.getProjectId())) {
            log.warn("项目 Agent 禁止直接暴露非渐进式 MCP 远端工具，projectId={} server={} mcpId={}",
                    config.getProjectId(), config.getName(), value(config.getMcpId()));
            if (processManager != null) {
                processManager.recordToolRoutingWarning(routingWarning(
                        config,
                        "PROJECT_MANAGED_MCP_REQUIRED"));
            }
            return OpsMcpProgressiveExposure.denied();
        }
        return OpsMcpProgressiveExposure.legacy();
    }

    public Map<String, Object> selectAndHydrate(
            OpsMcpServerConfig config,
            String remoteToolName) {
        ProgressiveMcpProcessManager processManager = requiredManager();
        String projectId = value(config == null ? null : config.getProjectId());
        String mcpId = value(
                config == null ? null : config.getMcpId(),
                config == null ? "" : config.getName());
        String toolId = value(config == null ? null : config.getToolId(), mcpId);
        String stage = remoteCallPolicy.stage(config).name();
        String capability = value(config == null || config.getToolCapabilities() == null
                ? null
                : config.getToolCapabilities().get(remoteToolName));
        Map<String, Object> selectionPayload = Map.of(
                "toolId", toolId,
                "mcpId", mcpId,
                "agentId", value(config == null ? null : config.getAgentId()),
                "nodeId", value(config == null ? null : config.getNodeId()),
                "runId", value(config == null ? null : config.getRunId()),
                "stage", stage,
                "capability", capability,
                "limit", 1);
        McpDiscoverySelectionResult route = processManager.select(new McpDiscoverySelectionRequest(
                projectId,
                capability,
                toolId,
                1,
                value(config == null ? null : config.getAgentId()),
                value(config == null ? null : config.getNodeId()),
                value(config == null ? null : config.getRunId()),
                "",
                "",
                true,
                stage,
                selectionPayload));
        if (!route.selected()) {
            throw new SecurityException("项目未授权 MCP 工具或工具路由未命中：" + toolId);
        }
        return processManager.hydrateSchema(new McpSchemaHydrationRequest(
                projectId,
                toolId,
                value(remoteToolName),
                value(config == null ? null : config.getAgentId()),
                "",
                McpAuthoritativeToolDefinition.empty())).view();
    }

    /** Recheck current local authorization without rebuilding/persisting the summary. */
    public boolean authorizedForCurrentStage(OpsMcpServerConfig config,String name) {
        return currentlyAuthorizedNames(config).contains(name);
    }

    public java.util.Set<String> currentlyAuthorizedNames(OpsMcpServerConfig config) {
        var manager = processManagerSupplier.get();
        if (!progressiveCandidate(config,manager)) return java.util.Set.of();
        var tools = manager.runtimeExecutableTools(value(config.getProjectId()),
                value(config.getToolId(),value(config.getMcpId(),config.getName())),
                config.getAllowedTools(),config.getBlockedTools(),catalogStage(config));
        return tools == null ? java.util.Set.of() : tools.stream().map(tool -> value(tool.get("toolName")))
                .filter(name -> !name.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void recordPreRemoteFailure(
            OpsMcpServerConfig config,
            String remoteToolName,
            String inputSummary,
            Exception error,
            long durationMs,
            Map<String, Object> auditMetadata) {
        try {
            ProgressiveMcpProcessManager processManager = processManagerSupplier.get();
            if (processManager == null || config == null) {
                return;
            }
            boolean blocked = error instanceof SecurityException;
            Map<String, Object> metadata = auditMetadata == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(auditMetadata);
            if (blocked
                    && error != null
                    && StringUtils.hasText(error.getMessage())
                    && remoteCallPolicy.requiresChangePackage(error.getMessage())) {
                metadata.put("blockReason", "MCP_TOOL_REQUIRES_CHANGE_PACKAGE");
            }
            String mcpId = value(config.getMcpId(), config.getName());
            String toolId = value(config.getToolId(), mcpId);
            processManager.recordMcpCall(new McpCommands.RuntimeCall(
                    value(config.getProjectId()),
                    value(config.getAgentId()),
                    value(config.getNodeId()),
                    value(config.getRunId()),
                    toolId,
                    mcpId,
                    StringUtils.hasText(remoteToolName) ? remoteToolName : toolId,
                    blocked ? "BLOCKED" : "FAILED",
                    value(inputSummary, "{}"),
                    error == null ? "" : error.getMessage(),
                    durationMs,
                    metadata));
        } catch (Exception auditError) {
            log.debug("MCP 远端调用前审计写入失败，已忽略：{}", auditError.getMessage());
        }
    }

    private String catalogStage(OpsMcpServerConfig config) {
        return config != null && "PREPARE_CHANGE".equalsIgnoreCase(value(config.getRuntimeAuthority()))
                ? "PREPARE_CHANGE" : remoteCallPolicy.stage(config).name();
    }

    private boolean progressiveCandidate(
            OpsMcpServerConfig config,
            ProgressiveMcpProcessManager processManager) {
        return config != null
                && Boolean.TRUE.equals(config.getProgressiveManaged())
                && processManager != null
                && StringUtils.hasText(config.getProjectId());
    }

    private ProgressiveMcpProcessManager requiredManager() {
        ProgressiveMcpProcessManager processManager = processManagerSupplier.get();
        if (processManager == null) {
            throw new IllegalStateException("PROGRESSIVE_MCP_PROCESS_MANAGER_UNAVAILABLE");
        }
        return processManager;
    }

    private McpCommands.RoutingWarning routingWarning(
            OpsMcpServerConfig config,
            String reason) {
        return new McpCommands.RoutingWarning(
                value(config == null ? null : config.getProjectId()),
                value(config == null ? null : config.getAgentId()),
                value(config == null ? null : config.getNodeId()),
                value(config == null ? null : config.getRunId()),
                value(config == null ? null : config.getMcpId(),
                        config == null ? "" : config.getName()),
                "RESOURCE_WARN",
                Map.of(
                        "reason", value(reason),
                        "mcpId", value(config == null ? null : config.getMcpId(),
                                config == null ? "" : config.getName()),
                        "server", value(config == null ? null : config.getName())));
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String value(Object value, String fallback) {
        String text = value(value);
        return StringUtils.hasText(text) ? text : value(fallback);
    }
}
