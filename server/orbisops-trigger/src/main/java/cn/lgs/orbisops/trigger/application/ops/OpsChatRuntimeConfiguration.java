package cn.lgs.orbisops.trigger.application.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for synchronous Chat runtime execution. */
@Configuration
public class OpsChatRuntimeConfiguration {

    @Bean
    public OpsChatRuntimeSettings opsChatRuntimeSettings(
            @Value("${orbisops.chat.sync-timeout-seconds:90}") long syncTimeoutSeconds) {
        return new OpsChatRuntimeSettings(syncTimeoutSeconds);
    }
}
