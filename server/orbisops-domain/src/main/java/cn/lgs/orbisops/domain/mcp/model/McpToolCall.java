package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

public record McpToolCall(String callId,
                          String projectId,
                          String agentId,
                          String nodeId,
                          String runId,
                          String toolId,
                          String mcpId,
                          String toolName,
                          String riskLevel,
                          boolean readOnly,
                          String status,
                          String inputJson,
                          String outputJson,
                          Long durationMs,
                          String errorMessage,
                          LocalDateTime createTime) {

    public McpToolCall {
        callId = required(callId, "MCP_TOOL_CALL_ID_REQUIRED");
        projectId = required(projectId, "MCP_TOOL_CALL_PROJECT_REQUIRED");
        agentId = safe(agentId);
        nodeId = safe(nodeId);
        runId = safe(runId);
        toolId = required(toolId, "MCP_TOOL_CALL_TOOL_ID_REQUIRED");
        mcpId = safe(mcpId);
        toolName = required(toolName, "MCP_TOOL_CALL_TOOL_NAME_REQUIRED");
        riskLevel = safe(riskLevel).isBlank() ? "HIGH" : riskLevel.trim().toUpperCase();
        status = safe(status).isBlank() ? "UNKNOWN" : status.trim().toUpperCase();
        inputJson = safe(inputJson);
        outputJson = safe(outputJson);
        errorMessage = safe(errorMessage);
    }

    private static String required(String value, String reasonCode) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
