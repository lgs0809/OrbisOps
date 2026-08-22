package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.trigger.ops.change.OpsLandingRecoverySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsLandingRecoveryConfiguration {

    @Bean
    public OpsLandingRecoverySettings opsLandingRecoverySettings(
            @Value("${orbisops.approved-landing.recovery.enabled:true}") boolean enabled,
            @Value("${orbisops.approved-landing.recovery.batch-size:20}") int batchSize,
            @Value("${orbisops.approved-landing.recovery.reconciliation-timeout-seconds:120}")
            int reconciliationTimeoutSeconds) {
        return new OpsLandingRecoverySettings(
                enabled,
                batchSize,
                reconciliationTimeoutSeconds);
    }
}
