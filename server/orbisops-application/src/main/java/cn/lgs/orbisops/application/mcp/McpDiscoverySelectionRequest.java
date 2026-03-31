package cn.lgs.orbisops.application.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpDiscoverySelectionRequest(
        String projectId,
        String capability,
        String explicitToolId,
        int limit,
        String agentId,
        String nodeId,
        String runId,
        String userId,
        String userRequest,
        boolean preferReadOnly,
        String stage,
        Map<String, Object> auditPayload) {

    public McpDiscoverySelectionRequest {
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        capability = text(capability);
        explicitToolId = text(explicitToolId);
        if (limit < 0) throw new IllegalArgumentException("MCP_DISCOVERY_LIMIT_INVALID");
        agentId = text(agentId);
        nodeId = text(nodeId);
        runId = text(runId);
        userId = text(userId);
        userRequest = text(userRequest);
        stage = text(stage);
        auditPayload = auditPayload == null || auditPayload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(auditPayload));
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
