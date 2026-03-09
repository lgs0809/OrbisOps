package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientModelSyncUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiClientModelSyncConfiguration {

    @Bean
    public AiClientModelSyncUseCase aiClientModelSyncUseCase(
            AiClientApiCatalogUseCase apiCatalogUseCase,
            IAiClientModelConfigRepository modelRepository,
            OpsConfigAuditService auditService) {
        return new AiClientModelSyncUseCase(
                new OpsAiClientModelSyncTargetAdapter(apiCatalogUseCase),
                new OpsAiClientModelSyncHttpProtocolAdapter(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build()),
                new OpsAiClientModelSyncCatalogAdapter(modelRepository),
                new OpsAiClientModelSyncAuditAdapter(auditService));
    }
}
