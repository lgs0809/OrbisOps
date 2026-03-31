package cn.lgs.orbisops.application.mcp;

public record McpSchemaHydrationRequest(
        String projectId,
        String toolId,
        String remoteToolName,
        String agentId,
        String userId,
        McpAuthoritativeToolDefinition authoritativeDefinition) {

    public McpSchemaHydrationRequest {
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
        remoteToolName = text(remoteToolName);
        agentId = text(agentId);
        userId = text(userId);
        authoritativeDefinition = authoritativeDefinition == null
                ? McpAuthoritativeToolDefinition.empty()
                : authoritativeDefinition;
    }

    public boolean remoteToolRequested() {
        return !remoteToolName.isBlank();
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
