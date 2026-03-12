package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpClientAuditPort;
import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientCatalogUseCase;
import cn.lgs.orbisops.application.config.McpClientRuntimeCachePort;
import cn.lgs.orbisops.application.config.McpTransportConfigProtectionPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring assembly for the MCP client catalog Application boundary. */
@Configuration
public class McpClientCatalogConfiguration {

    @Bean
    public McpClientAuditPort mcpClientAuditPort(OpsConfigAuditService auditService) {
        return new OpsMcpClientAuditAdapter(auditService);
    }

    @Bean
    public McpClientRuntimeCachePort mcpClientRuntimeCachePort(
            ObjectProvider<OpsMcpToolProvider> toolProvider) {
        return new OpsMcpClientRuntimeCacheAdapter(toolProvider.getIfAvailable());
    }

    @Bean
    public McpTransportConfigProtectionPort mcpTransportConfigProtectionPort() {
        return new OpsMcpTransportConfigProtectionAdapter();
    }

    @Bean
    public McpClientCatalogUseCase mcpClientCatalogUseCase(
            McpClientCatalogPort catalogPort,
            McpClientRuntimeCachePort runtimeCachePort,
            McpClientAuditPort auditPort,
            McpTransportConfigProtectionPort protectionPort) {
        return new McpClientCatalogUseCase(
                catalogPort,
                runtimeCachePort,
                auditPort,
                protectionPort);
    }
}
