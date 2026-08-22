package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRuntimeResourceAssemblerConfiguration {

    @Bean
    public OpsRuntimeModelResolver opsRuntimeModelResolver(
            @Qualifier("openAiChatModel") ObjectProvider<ChatModel> namedDefaultModelProvider,
            ObjectProvider<ChatModel> fallbackModelProvider,
            ObjectProvider<AiClientModelCatalogPort> modelRepositoryProvider,
            ObjectProvider<AiClientApiCatalogPort> apiRepositoryProvider,
            ObjectProvider<ModelDefaultPolicyApplicationService> defaultPolicyProvider,
            ModelAvailabilityPort aiModelAvailability,
            OpsSecretResolver secretResolver,
            OpsRuntimeModelSettings settings) {
        return new OpsRuntimeModelResolver(
                namedDefaultModelProvider::getIfAvailable,
                fallbackModelProvider::getIfAvailable,
                modelRepositoryProvider::getIfAvailable,
                apiRepositoryProvider::getIfAvailable,
                defaultPolicyProvider::getIfAvailable,
                aiModelAvailability,
                secretResolver,
                settings);
    }
}
