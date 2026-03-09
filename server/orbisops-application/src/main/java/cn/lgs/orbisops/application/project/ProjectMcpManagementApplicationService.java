package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.mcp.McpReviewedToolPolicySnapshot;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ProjectMcpManagementApplicationService {

    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectWorkspaceProjectionPort workspacePort;
    private final ProjectMcpUpdatePreparationPort preparationPort;
    private final ProjectMcpReviewedPolicyPort runtimeQueries;

    public ProjectMcpManagementApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectWorkspaceProjectionPort workspacePort,
            ProjectMcpUpdatePreparationPort preparationPort,
            ProjectMcpReviewedPolicyPort runtimeQueries) {
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (workspacePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_PORT_REQUIRED");
        }
        if (preparationPort == null) {
            throw new IllegalArgumentException("PROJECT_MCP_UPDATE_PREPARATION_PORT_REQUIRED");
        }
        if (runtimeQueries == null) {
            throw new IllegalArgumentException("MCP_RUNTIME_QUERY_REQUIRED");
        }
        this.catalogService = catalogService;
        this.workspacePort = workspacePort;
        this.preparationPort = preparationPort;
        this.runtimeQueries = runtimeQueries;
    }

    public Map<String, Object> update(
            String projectId,
            String mcpId,
            Map<String, Object> request) {
        return catalogService.view(updateDefinition(projectId, mcpId, request));
    }

    public ProjectMcpDefinition updateDefinition(
            String projectId,
            String mcpId,
            Map<String, Object> request) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String toolId = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        ProjectMcpDefinition current = catalogService.find(id, toolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "项目 MCP 不存在：" + toolId));
        Map<String, Object> command = request == null
                ? Map.of()
                : new LinkedHashMap<>(request);
        Map<String, Object> transportConfig =
                new LinkedHashMap<>(current.transportConfig());
        String resourceType = current.resourceType();

        String requestedName = text(
                command.get("toolName"),
                text(command.get("mcpName"), ""));
        String targetName = requestedName.isBlank() ? current.mcpName() : requestedName;
        List<String> targetAllowedActions = command.containsKey("allowedActions")
                ? strings(command.get("allowedActions"))
                : current.allowedActions();
        ProjectMcpRiskLevel targetRiskLevel = command.containsKey("riskLevel")
                ? ProjectMcpRiskLevel.failClosed(text(command.get("riskLevel"), "HIGH"))
                : current.riskLevel();
        boolean targetReadOnly = command.containsKey("readOnly")
                ? bool(command.get("readOnly"), false)
                : current.readOnly();
        Map<String, Object> targetPermissionPolicy = command.containsKey("permissionPolicy")
                ? preparationPort.enrichPermission(
                        resourceType,
                        map(command.get("permissionPolicy")))
                : current.permissionPolicy();
        if (targetPermissionPolicy == null) {
            targetPermissionPolicy = Map.of();
        }
        if (command.containsKey("resolvedTransportConfig")
                || command.containsKey("transportConfig")) {
            transportConfig.putAll(map(command.containsKey("resolvedTransportConfig")
                    ? command.get("resolvedTransportConfig")
                    : command.get("transportConfig")));
        }
        if (command.containsKey("remoteToolMetadata")) {
            transportConfig.put("remoteToolMetadata",
                    map(command.get("remoteToolMetadata")));
        }
        if (command.containsKey("remoteTools")) {
            transportConfig.put("remoteTools", tools(command.get("remoteTools")));
        }
        int targetRequestTimeout = command.containsKey("requestTimeout")
                ? integer(command.get("requestTimeout"), current.requestTimeout())
                : current.requestTimeout();
        ProjectMcpStatus targetStatus = command.containsKey("status")
                ? ProjectMcpStatus.from(text(command.get("status"), current.status().name()))
                : current.status();
        copyGovernance(command, transportConfig, "connectionStatus", "REGISTERED");
        copyGovernance(command, transportConfig, "policyStatus", "PENDING_REVIEW");
        copyGovernance(command, transportConfig, "discoveryError", "");
        if ("external_mcp".equals(resourceType)
                && targetStatus == ProjectMcpStatus.ENABLED) {
            List<String> reviewedTools = reviewedExternalToolNames(
                    id,
                    toolId,
                    transportConfig.get("remoteTools"));
            if (reviewedTools.isEmpty()) {
                throw new SecurityException(
                        "MCP_POLICY_REVIEW_REQUIRED：外部 MCP 至少需要一个 schemaHash 匹配的 ACTIVE/HUMAN_REVIEWED Tool Policy");
            }
            targetAllowedActions = reviewedTools;
            transportConfig.put("policyStatus", "ACTIVE");
            transportConfig.put("connectionStatus", "ENABLED");
        }

        if (!transportConfig.containsKey("remoteToolMetadata")
                && !transportConfig.containsKey("remoteTools")) {
            Map<String, Object> enrichedTransport =
                    preparationPort.enrichTransportMetadata(
                            resourceType,
                            targetAllowedActions,
                            transportConfig);
            if (enrichedTransport != null) {
                transportConfig = new LinkedHashMap<>(enrichedTransport);
            }
        }
        ProjectMcpDefinition saved = catalogService.save(current.update(
                targetName,
                transportConfig,
                targetAllowedActions,
                targetRiskLevel,
                targetReadOnly,
                targetPermissionPolicy,
                targetRequestTimeout,
                targetStatus,
                LocalDateTime.now()));
        workspacePort.materializeMcp(saved);
        return saved;
    }

    public Map<String, Object> updateStatus(
            String projectId,
            String mcpId,
            String status) {
        return catalogService.view(updateStatusDefinition(projectId, mcpId, status));
    }

    public ProjectMcpDefinition updateStatusDefinition(
            String projectId,
            String mcpId,
            String status) {
        return updateDefinition(projectId, mcpId, Map.of(
                "status", text(status, "DISABLED")));
    }

    private List<String> reviewedExternalToolNames(
            String projectId,
            String mcpId,
            Object remoteToolsValue) {
        List<Map<String, Object>> remoteTools = tools(remoteToolsValue);
        if (remoteTools.isEmpty()) {
            return List.of();
        }
        List<McpReviewedToolPolicySnapshot> policies =
                runtimeQueries.reviewedPolicies(projectId, 1000);
        LinkedHashSet<String> reviewed = new LinkedHashSet<>();
        for (Map<String, Object> remoteTool : remoteTools) {
            String toolName = text(remoteTool.get("toolName"), "");
            String schemaHash = text(remoteTool.get("schemaHash"), "");
            if (toolName.isBlank() || schemaHash.isBlank()) {
                continue;
            }
            boolean active = policies.stream().anyMatch(policy ->
                    mcpId.equals(policy.mcpId())
                            && toolName.equals(policy.toolName())
                            && schemaHash.equals(policy.schemaHash())
                            && policy.activeAndHumanReviewed());
            if (active) {
                reviewed.add(toolName);
            }
        }
        return List.copyOf(reviewed);
    }

    private void copyGovernance(
            Map<String, Object> command,
            Map<String, Object> transportConfig,
            String key,
            String fallback) {
        if (command.containsKey(key)) {
            transportConfig.put(key, text(command.get(key), fallback));
        }
    }

    private Map<String, Object> map(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<Map<String, Object>> tools(Object source) {
        if (!(source instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : iterable) {
            result.add(map(value));
        }
        return List.copyOf(result);
    }

    private List<String> strings(Object source) {
        if (source == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (source instanceof Iterable<?> iterable) {
            iterable.forEach(item -> add(values, item));
        } else {
            for (String item : String.valueOf(source).split("[,，\\n]")) {
                add(values, item);
            }
        }
        return values.stream().distinct().toList();
    }

    private void add(List<String> values, Object input) {
        String normalized = text(input, "");
        if (!normalized.isBlank()) {
            values.add(normalized);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = text(value, "");
        return normalized.isBlank()
                ? fallback
                : "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "enabled".equalsIgnoreCase(normalized);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
