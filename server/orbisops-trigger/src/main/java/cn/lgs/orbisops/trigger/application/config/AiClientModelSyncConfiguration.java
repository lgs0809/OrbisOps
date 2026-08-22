package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelSyncUseCase;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiClientModelSyncConfiguration {

    @Bean
    public AiClientModelSyncUseCase aiClientModelSyncUseCase(
            AiClientApiCatalogUseCase apiCatalogUseCase,
            AiClientModelCatalogPort modelCatalogPort,
            OpsConfigAuditService auditService,
            OpsSecretResolver secretResolver) {
        return new AiClientModelSyncUseCase(
                new OpsAiClientModelSyncTargetAdapter(apiCatalogUseCase, secretResolver),
                new OpsAiClientModelSyncHttpProtocolAdapter(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build()),
                new OpsAiClientModelSyncCatalogAdapter(modelCatalogPort),
                new OpsAiClientModelSyncAuditAdapter(auditService));
    }
}
