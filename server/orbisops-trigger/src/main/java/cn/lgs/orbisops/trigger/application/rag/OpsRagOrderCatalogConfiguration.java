package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderCatalogUseCase;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OpsRagOrderCatalogConfiguration {

    @Bean
    public RagOrderCatalogUseCase ragOrderCatalogUseCase(
            RagOrderCatalogPort catalogPort,
            OpsConfigAuditService auditService) {
        OpsRagOrderCatalogAdapter auditAdapter = new OpsRagOrderCatalogAdapter(auditService);
        return new RagOrderCatalogUseCase(
                catalogPort,
                auditAdapter,
                Clock.systemDefaultZone());
    }
}
