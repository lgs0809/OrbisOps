package cn.lgs.orbisops.trigger.application.resourcehealth;

import cn.lgs.orbisops.application.resourcehealth.ChannelResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ElasticsearchResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.McpRegistryResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ModelResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.MySqlResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.PgVectorResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.PrometheusResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthApplicationService;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Composition root for operational resource health queries. */
@Configuration
public class OpsResourceHealthApplicationConfiguration {

    @Bean
    public ResourceHealthApplicationService resourceHealthApplicationService(
            MySqlResourceHealthProbePort mySqlProbe,
            PgVectorResourceHealthProbePort pgVectorProbe,
            ElasticsearchResourceHealthProbePort elasticsearchProbe,
            PrometheusResourceHealthProbePort prometheusProbe,
            ModelResourceHealthProbePort modelProbe,
            McpRegistryResourceHealthProbePort mcpProbe,
            ChannelResourceHealthProbePort channelProbe,
            @Value("${orbisops.elasticsearch-url:http://127.0.0.1:9200}")
            String elasticsearchUrl,
            @Value("${orbisops.elasticsearch-index:}")
            String elasticsearchIndex,
            @Value("${orbisops.prometheus-url:http://127.0.0.1:9090}")
            String prometheusUrl,
            @Value("${orbisops.prometheus-job:}")
            String prometheusJob,
            @Value("${orbisops.prometheus-instance:}")
            String prometheusInstance,
            @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}")
            String ragVectorTableName) {
        return new ResourceHealthApplicationService(
                mySqlProbe,
                pgVectorProbe,
                elasticsearchProbe,
                prometheusProbe,
                modelProbe,
                mcpProbe,
                channelProbe,
                new ResourceHealthSettings(
                        elasticsearchUrl,
                        elasticsearchIndex,
                        prometheusUrl,
                        prometheusJob,
                        prometheusInstance,
                        ragVectorTableName),
                Clock.systemDefaultZone());
    }
}
