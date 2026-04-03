package cn.lgs.orbisops.trigger.application.modelpolicy;

import cn.lgs.orbisops.application.modelpolicy.ModelCatalogPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyPort;
import cn.lgs.orbisops.application.modelpolicy.ModelPolicyAuditPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsModelPolicyApplicationConfiguration {

    @Bean
    public ModelDefaultPolicyApplicationService modelDefaultPolicyApplicationService(
            ModelDefaultPolicyPort policyPort,
            ModelCatalogPort catalogPort,
            ModelPolicyAuditPort auditPort) {
        return new ModelDefaultPolicyApplicationService(policyPort, catalogPort, auditPort);
    }
}
