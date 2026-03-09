package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

/** Authoritative typed MCP catalog facts used when recording runtime calls. */
public record McpRuntimeToolSnapshot(
        String toolId,
        String mcpId,
        String toolName,
        McpRiskLevel riskLevel,
        boolean readOnly,
        boolean catalogBacked
) {

    public McpRuntimeToolSnapshot {
        toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
        mcpId = value(mcpId);
        toolName = text(toolName, toolId);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
    }

    public static McpRuntimeToolSnapshot fromDescriptor(
            String requestedToolId,
            McpProjectToolDescriptor descriptor) {
        if (descriptor == null) return fallback(requestedToolId);
        return new McpRuntimeToolSnapshot(
                requestedToolId,
                descriptor.mcpId(),
                descriptor.toolName(),
                descriptor.riskLevel(),
                descriptor.readOnly(),
                true);
    }

    public static McpRuntimeToolSnapshot fallback(String requestedToolId) {
        return new McpRuntimeToolSnapshot(
                requestedToolId,
                "",
                requestedToolId,
                McpRiskLevel.HIGH,
                false,
                false);
    }

    private static String required(String input, String reasonCode) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
