package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsRunCancellationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRuntimeToolTraceDecoratorConfiguration {

    @Bean
    public OpsRuntimeToolTraceDecorator opsRuntimeToolTraceDecorator(
            ObjectProvider<OpsRunCancellationRegistry> cancellationRegistryProvider) {
        return new OpsRuntimeToolTraceDecorator(
                cancellationRegistryProvider::getIfAvailable);
    }
}
