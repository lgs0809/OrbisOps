package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeSkillResolver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAgentLlmClientTest {

    @Test
    void shouldEnforceWallClockTimeoutForModelCalls() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ChatModel chatModel = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsRuntimeSkillResolver> skillProvider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        when(applicationContext.getBean("openAiChatModel", ChatModel.class)).thenReturn(chatModel);
        when(availability.isChatAvailable()).thenReturn(true);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"status\":\"ok\"}"))));
        });

        OpsAgentLlmClient client = new OpsAgentLlmClient(
                applicationContext,
                chatModelProvider,
                skillProvider,
                availability,
                executor,
                settings(1));

        long started = System.nanoTime();
        try {
            OpsLlmDegradationException error = assertThrows(OpsLlmDegradationException.class,
                    () -> client.chatJsonObject("timeout-test", "system", "user"));
            assertTrue(error.getMessage().contains("总时限"));
            assertTrue((System.nanoTime() - started) / 1_000_000L < 2_500L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldUseTheShorterNodeDeadline() {
        long started = System.nanoTime();
        OpsLlmDegradationException error = assertThrows(OpsLlmDegradationException.class,
                () -> OpsNodeDeadlineContext.withTimeout(1, () -> blockingClient(10)
                        .chatJsonObject("node-timeout-test", "system", "user")));
        assertTrue(error.getMessage().contains("总时限"));
        assertTrue((System.nanoTime() - started) / 1_000_000L < 2_500L);
    }

    @Test
    void shouldDetectAnExpiredNodeDeadlineBeforeContextIsCleared() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OpsNodeDeadlineContext.withTimeout(1, () -> {
                    try {
                        Thread.sleep(1_100L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", e);
                    }
                    OpsNodeDeadlineContext.assertNotExpired();
                    return null;
                }));

        assertTrue(error.getMessage().contains("节点执行超过配置的总时限"));
    }

    private OpsAgentLlmClient blockingClient(int modelTimeoutSeconds) {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ChatModel chatModel = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsRuntimeSkillResolver> skillProvider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        when(applicationContext.getBean("openAiChatModel", ChatModel.class)).thenReturn(chatModel);
        when(availability.isChatAvailable()).thenReturn(true);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"status\":\"ok\"}"))));
        });
        return new OpsAgentLlmClient(
                applicationContext,
                chatModelProvider,
                skillProvider,
                availability,
                executor,
                settings(modelTimeoutSeconds));
    }

    private OpsAgentLlmSettings settings(int modelTimeoutSeconds) {
        return new OpsAgentLlmSettings(
                true,
                6_000,
                modelTimeoutSeconds,
                false,
                12_000,
                true,
                false,
                false,
                1_200,
                false,
                16_000);
    }
}
