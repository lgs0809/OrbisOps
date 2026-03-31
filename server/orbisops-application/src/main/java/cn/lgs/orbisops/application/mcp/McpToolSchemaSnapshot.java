package cn.lgs.orbisops.application.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpToolSchemaSnapshot(
        String snapshotId,
        String projectId,
        String mcpId,
        String toolId,
        String toolName,
        String schemaHash,
        McpHydratedToolSchema schema,
        Map<String, Object> rawMetadata,
        boolean metadataComplete) {

    public McpToolSchemaSnapshot {
        snapshotId = required(snapshotId, "MCP_TOOL_SNAPSHOT_ID_REQUIRED");
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        mcpId = required(mcpId, "MCP_ID_REQUIRED");
        toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
        toolName = required(toolName, "MCP_TOOL_NAME_REQUIRED");
        schemaHash = required(schemaHash, "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED");
        if (schema == null) throw new IllegalArgumentException("MCP_HYDRATED_SCHEMA_REQUIRED");
        rawMetadata = rawMetadata == null || rawMetadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawMetadata));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
