package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsRunCancellationSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRunCancellationConfiguration {

    @Bean
    public OpsRunCancellationSettings opsRunCancellationSettings(
            @Value("${orbisops.runs.cancellation-retention-seconds:600}")
            long retentionSeconds) {
        return new OpsRunCancellationSettings(retentionSeconds);
    }
}
