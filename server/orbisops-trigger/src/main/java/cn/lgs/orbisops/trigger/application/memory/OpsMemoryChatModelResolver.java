package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/** Constructor-bound Spring AI model resolution and availability boundary for Memory adapters. */
final class OpsMemoryChatModelResolver {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ModelAvailabilityPort aiModelAvailability;

    OpsMemoryChatModelResolver(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            ModelAvailabilityPort aiModelAvailability) {
        this.applicationContext = applicationContext;
        this.chatModelProvider = chatModelProvider;
        this.aiModelAvailability = aiModelAvailability;
    }

    ChatModel resolve() {
        try {
            if (applicationContext != null) {
                return applicationContext.getBean("openAiChatModel", ChatModel.class);
            }
        } catch (RuntimeException ignored) {
        }
        return chatModelProvider == null
                ? null
                : chatModelProvider.getIfUnique();
    }

    boolean chatAvailable() {
        return aiModelAvailability != null
                && aiModelAvailability.isChatAvailable();
    }
}
