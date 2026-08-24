package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpToolCatalogService;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpCatalogConfiguration {
    @Bean public McpToolCatalogService mcpToolCatalogService(McpToolCatalogStore store) {
        return new McpToolCatalogService(store,java.time.Clock.systemUTC());
    }
}
