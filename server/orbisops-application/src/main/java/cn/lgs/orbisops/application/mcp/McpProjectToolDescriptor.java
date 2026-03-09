package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpProjectToolDescriptor(
        String projectId,
        String mcpId,
        String toolName,
        String resourceType,
        String transportType,
        List<String> allowedActions,
        McpRiskLevel riskLevel,
        boolean readOnly,
        Map<String, Object> permissionPolicy,
        int requestTimeout,
        ProjectMcpStatus status,
        Map<String, McpRemoteToolDescriptor> remoteTools) {

    public McpProjectToolDescriptor {
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        mcpId = required(mcpId, "MCP_ID_REQUIRED");
        toolName = text(toolName, mcpId);
        resourceType = text(resourceType, "custom");
        transportType = text(transportType, "stdio");
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        permissionPolicy = permissionPolicy == null || permissionPolicy.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(permissionPolicy));
        requestTimeout = requestTimeout <= 0 ? 30 : requestTimeout;
        status = status == null ? ProjectMcpStatus.PENDING_REVIEW : status;
        remoteTools = remoteTools == null || remoteTools.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(remoteTools));
    }

    public McpRemoteToolDescriptor remoteTool(String remoteToolName) {
        String normalized = text(remoteToolName, "");
        if (normalized.isBlank()) {
            return new McpRemoteToolDescriptor(
                    toolName, "", allowedActions, riskLevel, readOnly,
                    true, Map.of());
        }
        return remoteTools.getOrDefault(normalized, McpRemoteToolDescriptor.missing(normalized));
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
