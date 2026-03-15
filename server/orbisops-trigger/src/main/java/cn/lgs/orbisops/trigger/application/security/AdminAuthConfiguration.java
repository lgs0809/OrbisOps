package cn.lgs.orbisops.trigger.application.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminAuthConfiguration {

    @Bean
    public AdminAuthSettings adminAuthSettings(
            @Value("${orbisops.admin.auth.jwt-secret:dev-only-change-me}") String jwtSecret,
            @Value("${orbisops.admin.auth.jwt-ttl-hours:12}") long jwtTtlHours,
            @Value("${orbisops.admin.auth.service-token:}") String serviceToken,
            @Value("${orbisops.admin.auth.reject-weak-secrets:false}") boolean rejectWeakSecrets) {
        return new AdminAuthSettings(
                jwtSecret,
                jwtTtlHours,
                serviceToken,
                rejectWeakSecrets);
    }
}
