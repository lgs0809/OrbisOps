package cn.lgs.orbisops.trigger.application.audit;

import cn.lgs.orbisops.application.audit.AnalysisAuditApplicationService;
import cn.lgs.orbisops.application.audit.ConfigAuditApplicationService;
import cn.lgs.orbisops.domain.audit.adapter.repository.IAnalysisAuditRepository;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditPolicyRepository;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAuditApplicationConfiguration {

    @Bean
    public AnalysisAuditApplicationService analysisAuditApplicationService(
            IAnalysisAuditRepository repository,
            @Value("${orbisops.audit.max-records:200}") int maxRecords) {
        return new AnalysisAuditApplicationService(repository, maxRecords);
    }

    @Bean
    public ConfigAuditApplicationService configAuditApplicationService(
            IConfigAuditRepository audits,
            IConfigAuditPolicyRepository policies) {
        return new ConfigAuditApplicationService(audits, policies);
    }
}
