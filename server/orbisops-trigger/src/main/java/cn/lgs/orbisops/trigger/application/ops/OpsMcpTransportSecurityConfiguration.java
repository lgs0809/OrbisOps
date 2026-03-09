package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpTransportSecuritySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpTransportSecurityConfiguration {

    @Bean
    public OpsMcpTransportSecuritySettings opsMcpTransportSecuritySettings(
            @Value("${ops.mcp.security.enabled:true}") boolean enabled,
            @Value("${ops.mcp.security.allowed-transports:"
                    + OpsMcpTransportSecuritySettings.DEFAULT_TRANSPORTS + "}")
            String allowedTransports,
            @Value("${ops.mcp.security.allowed-stdio-commands:"
                    + OpsMcpTransportSecuritySettings.DEFAULT_STDIO_COMMANDS + "}")
            String allowedStdioCommands,
            @Value("${ops.mcp.security.allowed-sse-hosts:"
                    + OpsMcpTransportSecuritySettings.DEFAULT_REMOTE_HOSTS + "}")
            String allowedRemoteHosts,
            @Value("${ops.mcp.security.allowed-env-keys:}") String allowedEnvKeys,
            @Value("${ops.mcp.security.allowed-header-keys:"
                    + OpsMcpTransportSecuritySettings.DEFAULT_HEADER_KEYS + "}")
            String allowedHeaderKeys,
            @Value("${ops.mcp.security.max-timeout-seconds:60}") int maxTimeoutSeconds) {
        return OpsMcpTransportSecuritySettings.fromRaw(
                enabled,
                allowedTransports,
                allowedStdioCommands,
                allowedRemoteHosts,
                allowedEnvKeys,
                allowedHeaderKeys,
                maxTimeoutSeconds);
    }
}
