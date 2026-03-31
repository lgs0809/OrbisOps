package cn.lgs.orbisops.domain.mcp.model;

public record McpSchemaCacheEntry(String cacheId,
                                  String projectId,
                                  String toolId,
                                  String schemaJson,
                                  String status) {

    public McpSchemaCacheEntry {
        cacheId = required(cacheId, "MCP_SCHEMA_CACHE_ID_REQUIRED");
        projectId = required(projectId, "MCP_SCHEMA_CACHE_PROJECT_REQUIRED");
        toolId = required(toolId, "MCP_SCHEMA_CACHE_TOOL_REQUIRED");
        schemaJson = required(schemaJson, "MCP_SCHEMA_CACHE_JSON_REQUIRED");
        status = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
