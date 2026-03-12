package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckUseCase;
import cn.lgs.orbisops.application.config.AiClientApiHealthRecordPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiClientApiHealthConfiguration {

    @Bean
    public AiClientApiHealthCheckUseCase aiClientApiHealthCheckUseCase(
            AiClientApiCatalogUseCase catalogUseCase,
            AiClientApiHealthRecordPort healthRecordPort,
            OpsConfigAuditService auditService,
            OpsSecretResolver secretResolver) {
        OpsAiClientApiHealthResultAdapter auditAdapter =
                new OpsAiClientApiHealthResultAdapter(auditService);
        return new AiClientApiHealthCheckUseCase(
                new OpsAiClientApiHealthTargetAdapter(catalogUseCase, secretResolver),
                new OpsAiClientApiHealthHttpProbeAdapter(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .build()),
                new OpsAiClientApiHealthOperatorAdapter(),
                healthRecordPort,
                auditAdapter);
    }
}
