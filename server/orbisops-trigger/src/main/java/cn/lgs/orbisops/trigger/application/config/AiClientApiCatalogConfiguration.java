package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientApiCatalogConfiguration {

    @Bean
    public OpsAiClientApiCatalogAdapter opsAiClientApiCatalogAdapter(
            IAiClientApiConfigRepository repository,
            OpsConfigAuditService auditService) {
        return new OpsAiClientApiCatalogAdapter(
                repository,
                auditService);
    }

    @Bean
    public AiClientApiCatalogUseCase aiClientApiCatalogUseCase(
            OpsAiClientApiCatalogAdapter adapter) {
        return new AiClientApiCatalogUseCase(adapter, adapter);
    }
}
