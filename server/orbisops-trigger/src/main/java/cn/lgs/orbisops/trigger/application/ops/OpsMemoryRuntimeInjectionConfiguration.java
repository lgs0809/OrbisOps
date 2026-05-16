package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.memory.OpsMemoryRuntimeInjectionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMemoryRuntimeInjectionConfiguration {

    @Bean
    public OpsMemoryRuntimeInjectionSettings opsMemoryRuntimeInjectionSettings(
            @Value("${orbisops.memory.max-injection-count:8}") int maxInjectionCount) {
        return new OpsMemoryRuntimeInjectionSettings(maxInjectionCount);
    }
}
