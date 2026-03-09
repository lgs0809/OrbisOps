package cn.lgs.orbisops.application.config;

/** Command used to create or update one MCP client configuration. */
public record McpClientCommand(
        Long id,
        String mcpId,
        String mcpName,
        String transportType,
        String transportConfig,
        Integer requestTimeout,
        Integer status) {
}
