package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiHealthCheckRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiClientApiHealthConfiguration {

    @Bean
    public AiClientApiHealthCheckUseCase aiClientApiHealthCheckUseCase(
            AiClientApiCatalogUseCase catalogUseCase,
            IAiClientApiHealthCheckRepository healthRepository,
            OpsConfigAuditService auditService) {
        OpsAiClientApiHealthResultAdapter resultAdapter = new OpsAiClientApiHealthResultAdapter(
                healthRepository,
                auditService);
        return new AiClientApiHealthCheckUseCase(
                new OpsAiClientApiHealthTargetAdapter(catalogUseCase),
                new OpsAiClientApiHealthHttpProbeAdapter(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build()),
                new OpsAiClientApiHealthOperatorAdapter(),
                resultAdapter,
                resultAdapter);
    }
}
