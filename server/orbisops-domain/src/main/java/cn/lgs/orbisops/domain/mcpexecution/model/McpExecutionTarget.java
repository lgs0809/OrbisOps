package cn.lgs.orbisops.domain.mcpexecution.model;

public record McpExecutionTarget(
        String toolsetId,
        String toolName,
        String mcpId,
        String adapterType,
        String riskLevel,
        boolean readOnly,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        boolean enabled) {

    public McpExecutionTarget {
        toolsetId = required(toolsetId, "MCP_EXECUTION_TOOLSET_REQUIRED");
        toolName = required(toolName, "MCP_EXECUTION_TOOL_REQUIRED");
        mcpId = required(mcpId, "MCP_EXECUTION_MCP_ID_REQUIRED");
        adapterType = value(adapterType).isBlank() ? "MCP" : value(adapterType).toUpperCase();
        riskLevel = value(riskLevel).isBlank() ? "HIGH" : value(riskLevel).toUpperCase();
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
