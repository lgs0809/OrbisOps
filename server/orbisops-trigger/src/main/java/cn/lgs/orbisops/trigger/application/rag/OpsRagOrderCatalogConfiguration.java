package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderCatalogUseCase;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OpsRagOrderCatalogConfiguration {

    @Bean
    public RagOrderCatalogUseCase ragOrderCatalogUseCase(
            IAiClientRagOrderConfigRepository repository,
            OpsConfigAuditService auditService) {
        OpsRagOrderCatalogAdapter adapter = new OpsRagOrderCatalogAdapter(
                repository,
                auditService);
        return new RagOrderCatalogUseCase(
                adapter,
                adapter,
                Clock.systemDefaultZone());
    }
}
