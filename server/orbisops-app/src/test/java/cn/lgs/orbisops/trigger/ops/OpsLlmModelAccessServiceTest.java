package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsLlmModelAccessServiceTest {

    @Test
    void namedOpenAiModelHasPriorityOverUniqueProvider() {
        ApplicationContext context = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        ChatModel named = mock(ChatModel.class);
        when(availability.isChatAvailable()).thenReturn(true);
        when(context.getBean("openAiChatModel", ChatModel.class)).thenReturn(named);
        OpsLlmModelAccessService service = new OpsLlmModelAccessService(
                context,
                provider,
                availability);

        assertSame(named, service.resolveAvailable(true));
        assertTrue(service.available(true));
        verify(provider, never()).getIfUnique();
    }

    @Test
    void uniqueProviderIsUsedWhenNamedBeanCannotBeResolved() {
        ApplicationContext context = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        ChatModel fallback = mock(ChatModel.class);
        when(availability.isChatAvailable()).thenReturn(true);
        when(context.getBean("openAiChatModel", ChatModel.class))
                .thenThrow(new IllegalStateException("missing"));
        when(provider.getIfUnique()).thenReturn(fallback);
        OpsLlmModelAccessService service = new OpsLlmModelAccessService(
                context,
                provider,
                availability);

        assertSame(fallback, service.resolveAvailable(true));
    }

    @Test
    void disabledFlagShortCircuitsAvailabilityAndBeanResolution() {
        ApplicationContext context = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        OpsLlmModelAccessService service = new OpsLlmModelAccessService(
                context,
                provider,
                availability);

        assertNull(service.resolveAvailable(false));
        assertFalse(service.available(false));
        verifyNoInteractions(context, provider, availability);
    }

    @Test
    void unavailableModelCapabilityShortCircuitsBeanResolution() {
        ApplicationContext context = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        when(availability.isChatAvailable()).thenReturn(false);
        OpsLlmModelAccessService service = new OpsLlmModelAccessService(
                context,
                provider,
                availability);

        assertNull(service.resolveAvailable(true));
        verifyNoInteractions(context, provider);
    }

    @Test
    void statusPreservesOriginalKeysAndResolvesBeanEvenWhenDisabled() {
        ApplicationContext context = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        ChatModel model = mock(ChatModel.class);
        when(context.getBean("openAiChatModel", ChatModel.class)).thenReturn(model);
        when(availability.isChatAvailable()).thenReturn(true);
        OpsLlmModelAccessService service = new OpsLlmModelAccessService(
                context,
                provider,
                availability);

        Map<String, Object> status = service.status(false);

        assertEquals(Boolean.FALSE, status.get("enabled"));
        assertEquals(Boolean.TRUE, status.get("chatModelBeanAvailable"));
        assertEquals(model.getClass().getName(), status.get("chatModelBeanClass"));
        assertEquals(Boolean.FALSE, status.get("available"));
        verify(context).getBean("openAiChatModel", ChatModel.class);
        verify(availability).isChatAvailable();
    }
}
