package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRuntimeBuiltInToolContributorConfiguration {

    @Bean
    public OpsRuntimeBuiltInToolContributor opsRuntimeBuiltInToolContributor(
            OpsRuntimeToolContributorRegistry contributorRegistry) {
        return new OpsRuntimeBuiltInToolContributor(contributorRegistry);
    }
}
