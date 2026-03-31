package cn.lgs.orbisops.application.mcp;

public record McpRuntimeActivationRequest(
        String projectId,
        String runId,
        String actor,
        String mcpId,
        String toolName,
        String sessionId,
        String agentId,
        String userId,
        String reason,
        String stage,
        boolean trustedLandingRuntime,
        McpAuthoritativeToolDefinition authoritativeDefinition) {

    public McpRuntimeActivationRequest {
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        runId = required(runId, "MCP_RUN_ID_REQUIRED");
        actor = required(actor, "MCP_ACTOR_REQUIRED");
        mcpId = required(mcpId, "MCP_ID_REQUIRED");
        toolName = required(toolName, "MCP_TOOL_NAME_REQUIRED");
        sessionId = text(sessionId);
        agentId = text(agentId);
        userId = text(userId);
        reason = text(reason);
        stage = text(stage, "PREPARE");
        authoritativeDefinition = authoritativeDefinition == null
                ? McpAuthoritativeToolDefinition.empty()
                : authoritativeDefinition;
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String text(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
