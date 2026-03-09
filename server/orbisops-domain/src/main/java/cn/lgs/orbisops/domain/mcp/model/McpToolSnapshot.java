package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

public record McpToolSnapshot(String snapshotId,
                              String projectId,
                              String mcpId,
                              String toolId,
                              String toolName,
                              String schemaHash,
                              String schemaJson,
                              String metadataJson,
                              boolean metadataComplete,
                              String status,
                              LocalDateTime createTime,
                              LocalDateTime updateTime) {

    public McpToolSnapshot {
        snapshotId = required(snapshotId, "MCP_TOOL_SNAPSHOT_ID_REQUIRED");
        projectId = required(projectId, "MCP_TOOL_SNAPSHOT_PROJECT_REQUIRED");
        mcpId = required(mcpId, "MCP_TOOL_SNAPSHOT_MCP_REQUIRED");
        toolId = safe(toolId);
        toolName = required(toolName, "MCP_TOOL_SNAPSHOT_TOOL_REQUIRED");
        schemaHash = required(schemaHash, "MCP_TOOL_SNAPSHOT_SCHEMA_HASH_REQUIRED");
        schemaJson = required(schemaJson, "MCP_TOOL_SNAPSHOT_SCHEMA_REQUIRED");
        metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
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
