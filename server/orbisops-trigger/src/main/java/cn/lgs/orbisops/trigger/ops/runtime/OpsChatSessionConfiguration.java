package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.chatsession.ChatSessionMemoryCatalog;
import cn.lgs.orbisops.application.chatsession.ChatSessionStoreApplicationService;
import cn.lgs.orbisops.domain.chatsession.adapter.repository.IChatSessionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OpsChatSessionConfiguration {

    @Bean
    public OpsChatSessionSettings opsChatSessionSettings(Environment environment) {
        return new OpsChatSessionSettings(environment.getProperty(
                "orbisops.chat.session.allow-in-memory-fallback",
                Boolean.class,
                false));
    }

    @Bean
    public ChatSessionStoreApplicationService chatSessionStoreApplicationService(
            ObjectProvider<IChatSessionRepository> repositoryProvider,
            OpsChatSessionSettings settings,
            OpsChatSessionFallbackReporter fallbackReporter) {
        return new ChatSessionStoreApplicationService(
                repositoryProvider.getIfAvailable(),
                new ChatSessionMemoryCatalog(),
                settings::allowInMemoryFallback,
                fallbackReporter::report);
    }
}
