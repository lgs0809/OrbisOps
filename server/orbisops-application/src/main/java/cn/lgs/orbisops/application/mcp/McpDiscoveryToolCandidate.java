package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpDiscoveryToolCandidate(
        String toolId,
        String toolName,
        String resourceType,
        List<String> allowedActions,
        McpRiskLevel riskLevel,
        boolean readOnly,
        ProjectMcpStatus status,
        String description,
        double score) {

    public McpDiscoveryToolCandidate {
        toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
        toolName = text(toolName, toolId);
        resourceType = text(resourceType, "custom");
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        status = status == null ? ProjectMcpStatus.PENDING_REVIEW : status;
        description = text(description, "");
        score = Math.max(0D, score);
    }

    public static McpDiscoveryToolCandidate from(McpProjectToolDescriptor definition, double score) {
        if (definition == null) throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        return new McpDiscoveryToolCandidate(
                definition.mcpId(),
                definition.toolName(),
                definition.resourceType(),
                definition.allowedActions(),
                definition.riskLevel(),
                definition.readOnly(),
                definition.status(),
                "",
                score);
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolId", toolId);
        result.put("toolName", toolName);
        result.put("resourceType", resourceType);
        result.put("allowedActions", allowedActions);
        result.put("riskLevel", riskLevel.name());
        result.put("readOnly", readOnly);
        result.put("status", status.name());
        result.put("description", description);
        result.put("score", score);
        return Map.copyOf(result);
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
