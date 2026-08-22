package cn.lgs.orbisops.trigger.http.admin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for node-local HTTP rate limiting. */
@Configuration
public class OpsRateLimitConfiguration {

    @Bean
    public OpsRateLimitSettings opsRateLimitSettings(
            @Value("${orbisops.rate-limit.enabled:true}") boolean enabled,
            @Value("${orbisops.rate-limit.per-user-per-minute:120}") int perUserPerMinute,
            @Value("${orbisops.rate-limit.per-agent-path-per-minute:240}") int perAgentPathPerMinute) {
        return new OpsRateLimitSettings(enabled, perUserPerMinute, perAgentPathPerMinute);
    }
}
