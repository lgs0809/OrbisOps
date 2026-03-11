package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMemoryChatModelResolverTest {

    @Test
    void namedOpenAiModelTakesPrecedenceOverUniqueProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel named = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        when(context.getBean("openAiChatModel", ChatModel.class)).thenReturn(named);
        when(provider.getIfUnique()).thenReturn(fallback);
        when(availability.isChatAvailable()).thenReturn(true);
        OpsMemoryChatModelResolver resolver = new OpsMemoryChatModelResolver(
                context,
                provider,
                availability);

        assertSame(named, resolver.resolve());
        assertTrue(resolver.chatAvailable());
    }

    @Test
    void failedNamedLookupFallsBackAndNullAvailabilityIsClosed() {
        ApplicationContext context = mock(ApplicationContext.class);
        ChatModel fallback = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(context.getBean("openAiChatModel", ChatModel.class))
                .thenThrow(new IllegalStateException("missing"));
        when(provider.getIfUnique()).thenReturn(fallback);
        OpsMemoryChatModelResolver resolver = new OpsMemoryChatModelResolver(
                context,
                provider,
                null);

        assertSame(fallback, resolver.resolve());
        assertFalse(resolver.chatAvailable());
    }
}
