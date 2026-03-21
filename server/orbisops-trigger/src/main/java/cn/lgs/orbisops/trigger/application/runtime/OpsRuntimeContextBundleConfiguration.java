package cn.lgs.orbisops.trigger.application.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for runtime context bundle creation. */
@Configuration
public class OpsRuntimeContextBundleConfiguration {

    @Bean
    public OpsRuntimeContextBundleSettings opsRuntimeContextBundleSettings(
            @Value("${orbisops.skill-runtime.selected-limit:6}") int selectedSkillLimit) {
        return new OpsRuntimeContextBundleSettings(selectedSkillLimit);
    }
}
