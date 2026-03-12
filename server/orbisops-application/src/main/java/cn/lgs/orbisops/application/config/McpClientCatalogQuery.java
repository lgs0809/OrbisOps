package cn.lgs.orbisops.application.config;

/** Compatibility query selectors for the MCP client catalog. */
public record McpClientCatalogQuery(
        String mcpId,
        String mcpName,
        String transportType,
        Integer status) {

    public static McpClientCatalogQuery all() {
        return new McpClientCatalogQuery(null, null, null, null);
    }
}
