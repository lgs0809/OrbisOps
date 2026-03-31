package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

public record McpRuntimeActivation(String activationId,
                                   String projectId,
                                   String runId,
                                   String sessionId,
                                   String agentId,
                                   String mcpId,
                                   String toolName,
                                   String schemaHash,
                                   String disclosureTier,
                                   String status,
                                   LocalDateTime expiresAt,
                                   String metadataJson,
                                   LocalDateTime createTime,
                                   LocalDateTime updateTime) {

    public McpRuntimeActivation {
        activationId = required(activationId, "MCP_ACTIVATION_ID_REQUIRED");
        projectId = required(projectId, "MCP_ACTIVATION_PROJECT_REQUIRED");
        runId = required(runId, "MCP_ACTIVATION_RUN_REQUIRED");
        sessionId = safe(sessionId);
        agentId = safe(agentId);
        mcpId = required(mcpId, "MCP_ACTIVATION_MCP_REQUIRED");
        toolName = required(toolName, "MCP_ACTIVATION_TOOL_REQUIRED");
        schemaHash = required(schemaHash, "MCP_ACTIVATION_SCHEMA_HASH_REQUIRED");
        disclosureTier = safe(disclosureTier).isBlank() ? "EXTENSION" : disclosureTier.trim().toUpperCase();
        status = safe(status).isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        if (expiresAt == null) throw new IllegalArgumentException("MCP_ACTIVATION_EXPIRY_REQUIRED");
        metadataJson = safe(metadataJson).isBlank() ? "{}" : metadataJson;
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
