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
            @Value("${ops.elasticsearch-url:http://127.0.0.1:9200}") String baseUrl,
            @Value("${ops.elasticsearch-index:}") String index,
            @Value("${ops.elasticsearch-timeout-seconds:5}") int timeoutSeconds,
            @Value("${ops.elasticsearch-sample-size:8}") int sampleSize) {
        return new OpsEsLogSettings(baseUrl, index, timeoutSeconds, sampleSize);
    }

    @Bean
    public OpsPrometheusSettings opsPrometheusSettings(
            @Value("${ops.prometheus-url:http://127.0.0.1:9090}") String baseUrl,
            @Value("${ops.prometheus-job:}") String jobName,
            @Value("${ops.prometheus-timeout-seconds:5}") int timeoutSeconds) {
        return new OpsPrometheusSettings(baseUrl, jobName, timeoutSeconds);
    }

    @Bean
    public OpsScheduledPrometheusScreeningService opsScheduledPrometheusScreeningService(
            OpsPrometheusSettings settings) {
        return new OpsScheduledPrometheusScreeningService(settings);
    }

    @Bean
    public OpsMySqlSlowSqlSettings opsMySqlSlowSqlSettings(
            @Value("${ops.mysql-slow-sql.enabled:true}") boolean enabled,
            @Value("${ops.mysql-slow-sql.sample-size:10}") int sampleSize,
            @Value("${ops.mysql-slow-sql.threshold-ms:500}") double thresholdMs,
            @Value("${ops.mysql-slow-sql.performance-schema-fallback:true}") boolean performanceSchemaFallback) {
        return new OpsMySqlSlowSqlSettings(enabled, sampleSize, thresholdMs, performanceSchemaFallback);
    }
}
