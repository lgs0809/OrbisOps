package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

public record McpTemplateCatalogEntry(
        long catalogId,
        McpTemplateDefinition definition,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public McpTemplateCatalogEntry {
        catalogId = Math.max(catalogId, 0L);
        if (definition == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_DEFINITION_REQUIRED");
        }
    }
}
