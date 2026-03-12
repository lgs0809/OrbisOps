package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapPlan;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultModelApiBootstrapConfiguration {

    @Bean
    public DefaultModelApiBootstrapSettings defaultModelApiBootstrapSettings(
            @Value("${orbisops.model-api-bootstrap.enabled:false}") boolean enabled,
            @Value("${orbisops.model-api-bootstrap.api-id:1001}") String apiId,
            @Value("${spring.ai.openai.base-url:}") String configuredBaseUrl,
            @Value("${spring.ai.openai.api-key:}") String configuredApiKey) {
        return new DefaultModelApiBootstrapSettings(
                enabled,
                apiId,
                configuredBaseUrl,
                configuredApiKey);
    }

    @Bean
    public DefaultModelApiBootstrapPlan defaultModelApiBootstrapPlan(
            DefaultModelApiBootstrapSettings settings) {
        return new DefaultModelApiBootstrapPlan(
                settings.enabled(),
                settings.apiId(),
                settings.configuredBaseUrl(),
                settings.configuredApiKey());
    }

    @Bean
    public DefaultModelApiBootstrapUseCase defaultModelApiBootstrapUseCase(
            AiClientApiCatalogPort catalogPort) {
        return new DefaultModelApiBootstrapUseCase(catalogPort);
    }
}
