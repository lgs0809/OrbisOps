package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAlertTriggerConfiguration {

    @Bean
    public OpsAlertTriggerSettings opsAlertTriggerSettings(
            @Value("${orbisops.alert-triggers.signature-max-skew-seconds:300}") long signatureMaxSkewSeconds,
            @Value("${orbisops.alert-triggers.outbox.max-attempts:8}") int outboxMaxAttempts,
            @Value("${orbisops.alert-triggers.outbox.lock-timeout-seconds:120}") int outboxLockTimeoutSeconds,
            @Value("${orbisops.alert-triggers.aggregation.debounce-seconds:120}") int aggregationDebounceSeconds,
            @Value("${orbisops.alert-triggers.aggregation.max-wait-seconds:900}") int aggregationMaxWaitSeconds,
            @Value("${orbisops.alert-triggers.project-queue.max-queued:100}") int projectMaxQueued,
            @Value("${orbisops.alert-triggers.project-queue.max-running:4}") int projectMaxRunning) {
        return new OpsAlertTriggerSettings(
                signatureMaxSkewSeconds,
                outboxMaxAttempts,
                outboxLockTimeoutSeconds,
                aggregationDebounceSeconds,
                aggregationMaxWaitSeconds,
                projectMaxQueued,
                projectMaxRunning);
    }
}
