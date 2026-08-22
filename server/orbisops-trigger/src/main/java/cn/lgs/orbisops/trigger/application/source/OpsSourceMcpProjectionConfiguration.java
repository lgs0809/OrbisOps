package cn.lgs.orbisops.trigger.application.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for generated source MCP projections. */
@Configuration
public class OpsSourceMcpProjectionConfiguration {

    @Bean
    public OpsSourceMcpProjectionSettings opsSourceMcpProjectionSettings(
            @Value("${orbisops.source-repository.command-timeout-seconds:8}") int requestTimeoutSeconds) {
        return new OpsSourceMcpProjectionSettings(requestTimeoutSeconds);
    }
}
