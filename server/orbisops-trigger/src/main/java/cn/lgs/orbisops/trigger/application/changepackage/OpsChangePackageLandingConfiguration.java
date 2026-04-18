package cn.lgs.orbisops.trigger.application.changepackage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsChangePackageLandingConfiguration {

    @Bean
    public OpsChangePackageLandingSettings opsChangePackageLandingSettings(
            @Value("${orbisops.approved-landing.enabled:false}") boolean enabled) {
        return new OpsChangePackageLandingSettings(enabled);
    }
}
