package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.trigger.job.OpsAlertOutboxJobSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAlertOutboxJobConfiguration {

    @Bean
    public OpsAlertOutboxJobSettings opsAlertOutboxJobSettings(
            @Value("${orbisops.alert-triggers.outbox.enabled:true}") boolean enabled,
            @Value("${orbisops.alert-triggers.outbox.batch-size:20}") int batchSize) {
        return new OpsAlertOutboxJobSettings(enabled, batchSize);
    }
}
