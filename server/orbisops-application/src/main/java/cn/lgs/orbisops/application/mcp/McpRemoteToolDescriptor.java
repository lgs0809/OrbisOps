package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpRemoteToolDescriptor(
        String toolName,
        String description,
        List<String> allowedActions,
        McpRiskLevel riskLevel,
        boolean readOnly,
        boolean metadataComplete,
        Map<String, Object> rawMetadata) {

    public McpRemoteToolDescriptor {
        toolName = required(toolName, "MCP_REMOTE_TOOL_NAME_REQUIRED");
        description = text(description);
        allowedActions = allowedActions == null ? List.of() : allowedActions.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        rawMetadata = rawMetadata == null || rawMetadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawMetadata));
    }

    public static McpRemoteToolDescriptor missing(String toolName) {
        return new McpRemoteToolDescriptor(
                toolName, "", List.of("UNKNOWN_MUTATING"),
                McpRiskLevel.HIGH, false, false, Map.of());
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
