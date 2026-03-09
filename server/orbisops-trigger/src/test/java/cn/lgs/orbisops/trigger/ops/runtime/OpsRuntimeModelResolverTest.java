package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeModelResolverTest {

    @Test
    void blankModelMustUseNamedDefaultAndAssertAvailability() {
        ChatModel namedDefault = mock(ChatModel.class);
        AiModelAvailability availability = mock(AiModelAvailability.class);
        OpsRuntimeModelResolver resolver = resolver(
                () -> namedDefault,
                () -> mock(ChatModel.class),
                () -> null,
                () -> null,
                availability,
                secretResolver(null));

        ChatModel resolved = resolver.resolve(context(null, new ArrayList<>()));

        assertSame(namedDefault, resolved);
        verify(availability).assertChatAvailable("Agent 运行资源装配");
    }

    @Test
    void namedDefaultFailureMustFallbackWithoutSpringApplicationContext() {
        ChatModel fallback = mock(ChatModel.class);
        OpsRuntimeModelResolver resolver = resolver(
                () -> {
                    throw new IllegalStateException("named bean unavailable");
                },
                () -> fallback,
                () -> null,
                () -> null,
                mock(AiModelAvailability.class),
                secretResolver(null));

        assertSame(fallback, resolver.resolve(context(null, new ArrayList<>())));
    }

    @Test
    void missingConfiguredModelMustRecordCompatibilityWarningAndFallback() {
        ChatModel fallback = mock(ChatModel.class);
        IAiClientModelConfigRepository modelRepository = mock(IAiClientModelConfigRepository.class);
        IAiClientApiConfigRepository apiRepository = mock(IAiClientApiConfigRepository.class);
        when(modelRepository.queryByModelId("missing-model")).thenReturn(null);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeModelResolver resolver = resolver(
                () -> fallback,
                () -> fallback,
                () -> modelRepository,
                () -> apiRepository,
                mock(AiModelAvailability.class),
                secretResolver(null));

        ChatModel resolved = resolver.resolve(context("missing-model", events));

        assertSame(fallback, resolved);
        assertTrue(events.stream().anyMatch(event ->
                "RESOURCE_WARN".equals(event.getEventType())
                        && event.getSummary().contains("Model 配置不存在或未启用")
                        && "AGENT:agent-1".equals(event.getPayload().get("owner"))));
    }

    @Test
    void unavailableRepositoriesMustFailOpenOnlyToExplicitDefaultModel() {
        ChatModel fallback = mock(ChatModel.class);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeModelResolver resolver = resolver(
                () -> fallback,
                () -> fallback,
                () -> null,
                () -> null,
                mock(AiModelAvailability.class),
                secretResolver(null));

        assertSame(fallback, resolver.resolve(context("model-1", events)));
        assertTrue(events.stream().anyMatch(event ->
                event.getSummary().contains("Model 配置库不可用")));
    }

    @Test
    void activeConfiguredModelMustBuildTypedOpenAiModelAndAttachMetadata() {
        IAiClientModelConfigRepository modelRepository = mock(IAiClientModelConfigRepository.class);
        IAiClientApiConfigRepository apiRepository = mock(IAiClientApiConfigRepository.class);
        AiClientConfigRecord model = AiClientConfigRecord.builder()
                .modelId("model-1")
                .modelName("gpt-test")
                .apiId("api-1")
                .status(1)
                .build();
        AiClientConfigRecord api = AiClientConfigRecord.builder()
                .apiId("api-1")
                .baseUrl("https://example.invalid")
                .apiKey("${env:OPS_TEST_KEY:fallback-key}")
                .completionsPath("/v1/chat/completions")
                .embeddingsPath("/v1/embeddings")
                .status(1)
                .build();
        when(modelRepository.queryByModelId("model-1")).thenReturn(model);
        when(apiRepository.queryByApiId("api-1")).thenReturn(api);
        OpsRuntimeResourceContext context = context("model-1", new ArrayList<>());
        OpsRuntimeModelResolver resolver = resolver(
                () -> mock(ChatModel.class),
                () -> mock(ChatModel.class),
                () -> modelRepository,
                () -> apiRepository,
                mock(AiModelAvailability.class),
                secretResolver("resolved-key"));

        ChatModel resolved = resolver.resolve(context);

        assertInstanceOf(OpenAiChatModel.class, resolved);
        assertEquals("gpt-test", context.getMetadata().get("modelName"));
        assertEquals("api-1", context.getMetadata().get("apiId"));
    }

    @Test
    void noDefaultModelMustRemainFailClosed() {
        OpsRuntimeModelResolver resolver = resolver(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                mock(AiModelAvailability.class),
                secretResolver(null));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(context(null, new ArrayList<>())));

        assertTrue(error.getMessage().contains("ChatModel 未初始化"));
    }

    @Test
    void settingsMustNormalizeNonPositiveTimeouts() {
        OpsRuntimeModelSettings settings = OpsRuntimeModelSettings.forTest(0, -1);

        assertEquals(1, settings.connectTimeoutSeconds());
        assertEquals(1, settings.readTimeoutSeconds());
    }

    private OpsRuntimeModelResolver resolver(
            java.util.function.Supplier<ChatModel> namedDefault,
            java.util.function.Supplier<ChatModel> fallback,
            java.util.function.Supplier<IAiClientModelConfigRepository> modelRepository,
            java.util.function.Supplier<IAiClientApiConfigRepository> apiRepository,
            AiModelAvailability availability,
            OpsSecretResolver secretResolver) {
        return new OpsRuntimeModelResolver(
                namedDefault,
                fallback,
                modelRepository,
                apiRepository,
                availability,
                secretResolver,
                OpsRuntimeModelSettings.forTest(5, 45));
    }

    private OpsSecretResolver secretResolver(String configuredValue) {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("OPS_TEST_KEY")).thenReturn(configuredValue);
        return new OpsSecretResolver(environment);
    }

    private OpsRuntimeResourceContext context(String modelId, List<OpsRuntimeEvent> events) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .modelId(modelId)
                .events(events)
                .build();
    }
}
