package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsToolExecutionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsToolExecutionConfiguration {

    @Bean
    public OpsToolExecutionSettings opsToolExecutionSettings(
            @Value("${ops.skill.execution.enabled:true}") boolean enabled,
            @Value("${ops.skill.execution.allow-review:false}") boolean allowReview,
            @Value("${ops.skill.execution.allow-production:false}") boolean allowProduction,
            @Value("${ops.skill.execution.max-timeout-seconds:60}") int maxTimeoutSeconds,
            @Value("${ops.skill.execution.max-output-bytes:262144}") long maxOutputBytes,
            @Value("${ops.skill.execution.max-concurrency:1}") int maxConcurrency) {
        return new OpsToolExecutionSettings(
                enabled,
                allowReview,
                allowProduction,
                maxTimeoutSeconds,
                maxOutputBytes,
                maxConcurrency);
    }
}
