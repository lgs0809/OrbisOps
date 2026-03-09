package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientModelCatalogConfiguration {

    @Bean
    public OpsAiClientModelCatalogAdapter aiClientModelCatalogAdapter(
            IAiClientModelConfigRepository repository,
            OpsConfigAuditService auditService) {
        return new OpsAiClientModelCatalogAdapter(repository, auditService);
    }

    @Bean
    public AiClientModelCatalogUseCase aiClientModelCatalogUseCase(
            OpsAiClientModelCatalogAdapter adapter) {
        return new AiClientModelCatalogUseCase(adapter, adapter);
    }
}
