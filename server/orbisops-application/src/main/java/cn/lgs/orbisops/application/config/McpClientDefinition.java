package cn.lgs.orbisops.application.config;

import java.time.LocalDateTime;

/** Narrow typed Application model for one MCP client configuration. */
public record McpClientDefinition(
        Long id,
        String mcpId,
        String mcpName,
        String transportType,
        String transportConfig,
        Integer requestTimeout,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public McpClientDefinition withProtectedTransportConfig(String protectedConfig) {
        return new McpClientDefinition(
                id,
                mcpId,
                mcpName,
                transportType,
                protectedConfig,
                requestTimeout,
                status,
                createTime,
                updateTime);
    }
}
