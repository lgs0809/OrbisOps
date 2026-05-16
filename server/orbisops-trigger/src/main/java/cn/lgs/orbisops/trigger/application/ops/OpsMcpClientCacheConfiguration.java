package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpClientCacheSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpClientCacheConfiguration {

    @Bean
    public OpsMcpClientCacheSettings opsMcpClientCacheSettings(
            @Value("${orbisops.mcp.cache.ttl-seconds:600}") long ttlSeconds,
            @Value("${orbisops.mcp.cache.cleanup-interval-ms:60000}") long cleanupIntervalMillis) {
        return new OpsMcpClientCacheSettings(ttlSeconds, cleanupIntervalMillis);
    }
}
