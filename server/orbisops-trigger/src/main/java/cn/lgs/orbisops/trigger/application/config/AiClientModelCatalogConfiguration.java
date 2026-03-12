package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelCatalogUseCase;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientModelCatalogConfiguration {

    @Bean
    public OpsAiClientModelCatalogAdapter aiClientModelCatalogAdapter(OpsConfigAuditService auditService) {
        return new OpsAiClientModelCatalogAdapter(auditService);
    }

    @Bean
    public AiClientModelCatalogUseCase aiClientModelCatalogUseCase(
            AiClientModelCatalogPort catalogPort,
            OpsAiClientModelCatalogAdapter auditAdapter) {
        return new AiClientModelCatalogUseCase(catalogPort, auditAdapter);
    }
}
