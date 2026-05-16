package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsEsLogSettings;
import cn.lgs.orbisops.trigger.ops.OpsMySqlSlowSqlSettings;
import cn.lgs.orbisops.trigger.ops.OpsPrometheusSettings;
import cn.lgs.orbisops.trigger.ops.OpsScheduledPrometheusScreeningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration boundary for datasource-oriented sub-agent settings. */
@Configuration
public class OpsDatasourceSubAgentConfiguration {

    @Bean
    public OpsEsLogSettings opsEsLogSettings(
            @Value("${orbisops.elasticsearch-url:http://127.0.0.1:9200}") String baseUrl,
            @Value("${orbisops.elasticsearch-index:}") String index,
            @Value("${orbisops.elasticsearch-timeout-seconds:5}") int timeoutSeconds,
            @Value("${orbisops.elasticsearch-sample-size:8}") int sampleSize) {
        return new OpsEsLogSettings(baseUrl, index, timeoutSeconds, sampleSize);
    }

    @Bean
    public OpsPrometheusSettings opsPrometheusSettings(
            @Value("${orbisops.prometheus-url:http://127.0.0.1:9090}") String baseUrl,
            @Value("${orbisops.prometheus-job:}") String jobName,
            @Value("${orbisops.prometheus-timeout-seconds:5}") int timeoutSeconds) {
        return new OpsPrometheusSettings(baseUrl, jobName, timeoutSeconds);
    }

    @Bean
    public OpsScheduledPrometheusScreeningService opsScheduledPrometheusScreeningService(
            OpsPrometheusSettings settings) {
        return new OpsScheduledPrometheusScreeningService(settings);
    }

    @Bean
    public OpsMySqlSlowSqlSettings opsMySqlSlowSqlSettings(
            @Value("${orbisops.mysql-slow-sql.enabled:true}") boolean enabled,
            @Value("${orbisops.mysql-slow-sql.sample-size:10}") int sampleSize,
            @Value("${orbisops.mysql-slow-sql.threshold-ms:500}") double thresholdMs,
            @Value("${orbisops.mysql-slow-sql.performance-schema-fallback:true}") boolean performanceSchemaFallback) {
        return new OpsMySqlSlowSqlSettings(enabled, sampleSize, thresholdMs, performanceSchemaFallback);
    }
}
