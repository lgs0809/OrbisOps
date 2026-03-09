package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
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
            ObjectProvider<IAiClientModelConfigRepository> modelRepositoryProvider,
            ObjectProvider<IAiClientApiConfigRepository> apiRepositoryProvider,
            AiModelAvailability aiModelAvailability,
            OpsSecretResolver secretResolver,
            OpsRuntimeModelSettings settings) {
        return new OpsRuntimeModelResolver(
                namedDefaultModelProvider::getIfAvailable,
                fallbackModelProvider::getIfAvailable,
                modelRepositoryProvider::getIfAvailable,
                apiRepositoryProvider::getIfAvailable,
                aiModelAvailability,
                secretResolver,
                settings);
    }
}
