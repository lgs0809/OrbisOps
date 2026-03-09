package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAlertTriggerConfiguration {

    @Bean
    public OpsAlertTriggerSettings opsAlertTriggerSettings(
            @Value("${ops.alert-triggers.signature-max-skew-seconds:300}") long signatureMaxSkewSeconds,
            @Value("${ops.alert-triggers.outbox.max-attempts:8}") int outboxMaxAttempts,
            @Value("${ops.alert-triggers.outbox.lock-timeout-seconds:120}") int outboxLockTimeoutSeconds,
            @Value("${ops.alert-triggers.aggregation.debounce-seconds:120}") int aggregationDebounceSeconds,
            @Value("${ops.alert-triggers.aggregation.max-wait-seconds:900}") int aggregationMaxWaitSeconds,
            @Value("${ops.alert-triggers.project-queue.max-queued:100}") int projectMaxQueued,
            @Value("${ops.alert-triggers.project-queue.max-running:4}") int projectMaxRunning) {
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
