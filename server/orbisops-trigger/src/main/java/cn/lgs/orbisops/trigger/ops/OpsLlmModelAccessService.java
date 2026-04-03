package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

/** Plain adapter boundary for ChatModel discovery and runtime availability. */
final class OpsLlmModelAccessService {

    private final ApplicationContext applicationContext;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ModelAvailabilityPort aiModelAvailability;

    OpsLlmModelAccessService(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            ModelAvailabilityPort aiModelAvailability) {
        this.applicationContext = applicationContext;
        this.chatModelProvider = chatModelProvider;
        this.aiModelAvailability = aiModelAvailability;
    }

    ChatModel resolveAvailable(boolean enabled) {
        if (!enabled || !aiModelAvailability.isChatAvailable()) {
            return null;
        }
        return resolve();
    }

    boolean available(boolean enabled) {
        return resolveAvailable(enabled) != null;
    }

    Map<String, Object> status(boolean enabled) {
        Map<String, Object> data = new LinkedHashMap<>();
        ChatModel chatModel = resolve();
        boolean chatAvailable = aiModelAvailability.isChatAvailable();
        data.put("enabled", enabled);
        data.put("chatModelBeanAvailable", chatModel != null);
        data.put("chatModelBeanClass",
                chatModel == null ? "" : chatModel.getClass().getName());
        data.put("available", enabled && chatAvailable && chatModel != null);
        return data;
    }

    private ChatModel resolve() {
        try {
            return applicationContext.getBean("openAiChatModel", ChatModel.class);
        } catch (Exception ignored) {
            return chatModelProvider.getIfUnique();
        }
    }
}
