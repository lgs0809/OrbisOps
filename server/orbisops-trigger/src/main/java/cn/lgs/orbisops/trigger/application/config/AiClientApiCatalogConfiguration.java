package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientApiCatalogConfiguration {

    @Bean
    public OpsAiClientApiCatalogAdapter opsAiClientApiCatalogAdapter(OpsConfigAuditService auditService) {
        return new OpsAiClientApiCatalogAdapter(auditService);
    }

    @Bean
    public AiClientApiCatalogUseCase aiClientApiCatalogUseCase(
            AiClientApiCatalogPort catalogPort,
            OpsAiClientApiCatalogAdapter auditAdapter) {
        return new AiClientApiCatalogUseCase(catalogPort, auditAdapter);
    }
}
