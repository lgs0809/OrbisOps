package cn.lgs.orbisops.trigger.application.toolset;

import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalAdapterSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsLocalAdapterConfiguration {

    @Bean
    public OpsLocalAdapterSettings opsLocalAdapterSettings(
            @Value("${orbisops.prometheus-url:http://127.0.0.1:9090}") String prometheusUrl,
            @Value("${orbisops.elasticsearch-url:http://127.0.0.1:9200}") String elasticsearchUrl,
            @Value("${orbisops.elasticsearch-index:}") String elasticsearchIndex,
            @Value("${orbisops.elasticsearch-index-whitelist:${orbisops.elasticsearch-index:}}") String elasticsearchIndexWhitelist,
            @Value("${orbisops.local-log.allowed-roots:./logs}") String allowedLogRoots,
            @Value("${orbisops.docker-compose.allowed-roots:./}") String allowedDockerComposeRoots,
            @Value("${orbisops.local-adapter.timeout-seconds:8}") int timeoutSeconds,
            @Value("${orbisops.local-adapter.max-rows:200}") int maxRows,
            @Value("${orbisops.local-adapter.max-response-bytes:65536}") int maxResponseBytes) {
        return new OpsLocalAdapterSettings(
                prometheusUrl,
                elasticsearchUrl,
                elasticsearchIndex,
                elasticsearchIndexWhitelist,
                allowedLogRoots,
                allowedDockerComposeRoots,
                timeoutSeconds,
                maxRows,
                maxResponseBytes);
    }
}
