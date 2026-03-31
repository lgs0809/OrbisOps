package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

public record McpRoutingDecision(String decisionId,
                                 String projectId,
                                 String agentId,
                                 String nodeId,
                                 String runId,
                                 String capability,
                                 String requestJson,
                                 String selectedToolsJson,
                                 String reason,
                                 String status,
                                 LocalDateTime createTime) {

    public McpRoutingDecision {
        decisionId = required(decisionId, "MCP_ROUTING_DECISION_ID_REQUIRED");
        projectId = required(projectId, "MCP_ROUTING_DECISION_PROJECT_REQUIRED");
        agentId = safe(agentId);
        nodeId = safe(nodeId);
        runId = safe(runId);
        capability = safe(capability);
        requestJson = json(requestJson, "{}");
        selectedToolsJson = json(selectedToolsJson, "[]");
        reason = safe(reason);
        status = safe(status).isBlank() ? "SELECTED" : status.trim().toUpperCase();
    }

    private static String required(String value, String reasonCode) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String json(String value, String fallback) {
        String normalized = safe(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
