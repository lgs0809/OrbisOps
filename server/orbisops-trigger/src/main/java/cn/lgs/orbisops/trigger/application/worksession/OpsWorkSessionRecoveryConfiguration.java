package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRecoverySettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsWorkSessionRecoveryConfiguration {

    @Bean
    public OpsWorkSessionRecoverySettings opsWorkSessionRecoverySettings(
            @Value("${ops.work-session.recovery.enabled:true}") boolean enabled,
            @Value("${ops.work-session.recovery.batch-size:100}") int batchSize) {
        return new OpsWorkSessionRecoverySettings(enabled, batchSize);
    }
}
