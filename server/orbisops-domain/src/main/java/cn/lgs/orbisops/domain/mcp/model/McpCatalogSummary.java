package cn.lgs.orbisops.domain.mcp.model;

public record McpCatalogSummary(String projectId,
                                String summaryJson,
                                int toolCount) {

    public McpCatalogSummary {
        projectId = required(projectId, "MCP_CATALOG_SUMMARY_PROJECT_REQUIRED");
        summaryJson = required(summaryJson, "MCP_CATALOG_SUMMARY_JSON_REQUIRED");
        toolCount = Math.max(toolCount, 0);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
