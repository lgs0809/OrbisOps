package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.capability.CapabilityReadinessEnvironmentPort;
import cn.lgs.orbisops.application.capability.CapabilityReadinessUseCase;
import cn.lgs.orbisops.application.changepackage.ChangePackageReadinessPort;
import cn.lgs.orbisops.trigger.ops.change.OpsLandingOperationRecoveryService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolResultStore;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolRuntimeReadiness;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring assembly for typed capability readiness derivation. */
@Configuration
public class OpsCapabilityReadinessConfiguration {

    @Bean
    public CapabilityReadinessEnvironmentPort capabilityReadinessEnvironmentPort(
            OpsToolResultStore toolResultStore,
            OpsTrustedProofService trustedProofService,
            OpsConfigAuditService auditService,
            ChangePackageReadinessPort changePackageReadiness,
            ObjectProvider<OpsLandingOperationRecoveryService> landingRecoveryProvider,
            OpsToolRuntimeReadiness toolRuntimeReadiness) {
        return new OpsCapabilityReadinessEnvironmentAdapter(
                toolResultStore,
                trustedProofService,
                auditService,
                changePackageReadiness,
                landingRecoveryProvider.getIfAvailable(),
                toolRuntimeReadiness);
    }

    @Bean
    public CapabilityReadinessUseCase capabilityReadinessUseCase(
            CapabilityReadinessEnvironmentPort environmentPort) {
        return new CapabilityReadinessUseCase(environmentPort);
    }
}
