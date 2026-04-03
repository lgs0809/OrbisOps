package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicySnapshot;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelPolicyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    void deterministicApprovalGraphAndDirectNodeDoNotResolveOrCallModels() {
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
        OpsRuntimeModelResolver resolver = resolver(
                () -> { throw new AssertionError("No model needed"); },
                () -> { throw new AssertionError("No model needed"); },
                () -> { throw new AssertionError("No model catalog needed"); },
                () -> { throw new AssertionError("No provider catalog needed"); }, availability, secretResolver(null));
        OpsWorkflowNode direct = OpsWorkflowNode.builder().nodeId("read").type("AGENT").mode("direct").build();
        var graph = OpsAgentDefinition.builder().agentId("deterministic")
                .nodes(List.of(OpsWorkflowNode.builder().type("START").build(), direct,
                        OpsWorkflowNode.builder().type("SUB_WORKFLOW").agent("child").build(),
                        OpsWorkflowNode.builder().type("HUMAN_APPROVAL").build(), OpsWorkflowNode.builder().type("END").build())).build();
        var context = OpsRuntimeResourceContext.builder().definition(graph).build();
        org.junit.jupiter.api.Assertions.assertNull(resolver.resolve(context));
        context.setNode(direct);
        org.junit.jupiter.api.Assertions.assertNull(resolver.resolve(context));
        org.mockito.Mockito.verifyNoInteractions(availability);
        assertEquals("NOT_REQUIRED_BY_DETERMINISTIC_NODES", context.getMetadata().get("modelResolution"));
        var childLlm = OpsWorkflowNode.builder().type("AGENT").mode("LLM").build();
        var child = OpsAgentDefinition.builder().nodes(List.of(childLlm)).build();
        assertTrue(OpsRuntimeModelRequirement.requiresChatModel(OpsRuntimeResourceContext.builder().definition(child).build()));
        assertTrue(OpsRuntimeModelRequirement.requiresChatModel(OpsRuntimeResourceContext.builder().node(childLlm).build()));
    }

    @Test
    void blankModelMustUseNamedDefaultAndAssertAvailability() {
        ChatModel namedDefault = mock(ChatModel.class);
        ModelAvailabilityPort availability = mock(ModelAvailabilityPort.class);
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
                mock(ModelAvailabilityPort.class),
                secretResolver(null));

        assertSame(fallback, resolver.resolve(context(null, new ArrayList<>())));
    }

    @Test
    void missingConfiguredModelMustFailWithoutSilentFallback() {
        ChatModel fallback = mock(ChatModel.class);
        AiClientModelCatalogPort modelRepository = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apiRepository = mock(AiClientApiCatalogPort.class);
        when(modelRepository.findByModelId("missing-model")).thenReturn(null);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeModelResolver resolver = resolver(
                () -> fallback,
                () -> fallback,
                () -> modelRepository,
                () -> apiRepository,
                mock(ModelAvailabilityPort.class),
                secretResolver(null));

        assertEquals("MODEL_BINDING_UNAVAILABLE", assertThrows(IllegalStateException.class,
                () -> resolver.resolve(context("missing-model", events))).getMessage());
        org.mockito.Mockito.verifyNoInteractions(fallback);
    }

    @Test
    void unavailableRepositoriesMustNotReplaceAnExplicitModelBinding() {
        ChatModel fallback = mock(ChatModel.class);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsRuntimeModelResolver resolver = resolver(
                () -> fallback,
                () -> fallback,
                () -> null,
                () -> null,
                mock(ModelAvailabilityPort.class),
                secretResolver(null));

        assertEquals("MODEL_CATALOG_UNAVAILABLE", assertThrows(IllegalStateException.class,
                () -> resolver.resolve(context("model-1", events))).getMessage());
        org.mockito.Mockito.verifyNoInteractions(fallback);
    }

    @Test
    void activeConfiguredModelMustBuildTypedOpenAiModelAndAttachMetadata() {
        AiClientModelCatalogPort modelRepository = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apiRepository = mock(AiClientApiCatalogPort.class);
        AiClientModelDefinition model = new AiClientModelDefinition(
                null, "model-1", "api-1", "gpt-test", null, null, null, 1, null, null);
        AiClientApiDefinition api = new AiClientApiDefinition(
                null,
                "api-1",
                null,
                null,
                "https://example.invalid",
                "${env:OPS_TEST_KEY:fallback-key}",
                "/v1/chat/completions",
                "/v1/embeddings",
                1,
                null,
                null);
        when(modelRepository.findByModelId("model-1")).thenReturn(model);
        when(apiRepository.findByApiId("api-1")).thenReturn(api);
        OpsRuntimeResourceContext context = context("model-1", new ArrayList<>());
        OpsRuntimeModelResolver resolver = resolver(
                () -> mock(ChatModel.class),
                () -> mock(ChatModel.class),
                () -> modelRepository,
                () -> apiRepository,
                mock(ModelAvailabilityPort.class),
                secretResolver("resolved-key"));

        ChatModel resolved = resolver.resolve(context);

        assertInstanceOf(OpenAiChatModel.class, resolved);
        assertEquals("gpt-test", context.getMetadata().get("modelName"));
        assertEquals("api-1", context.getMetadata().get("apiId"));
    }

    @Test
    void blankModelMustUseProjectDefaultPolicyBeforeEnvironmentFallback() {
        AiClientModelCatalogPort modelRepository = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apiRepository = mock(AiClientApiCatalogPort.class);
        ModelDefaultPolicyApplicationService policies = mock(ModelDefaultPolicyApplicationService.class);
        AiClientModelDefinition model = new AiClientModelDefinition(
                null, "model-1", "api-1", "gpt-project-default", null, null, null, 1, null, null);
        AiClientApiDefinition api = new AiClientApiDefinition(
                null,
                "api-1",
                null,
                null,
                "https://example.invalid",
                "${env:OPS_TEST_KEY:fallback-key}",
                "/v1/chat/completions",
                "/v1/embeddings",
                1,
                null,
                null);
        when(modelRepository.findByModelId("model-1")).thenReturn(model);
        when(apiRepository.findByApiId("api-1")).thenReturn(api);
        when(policies.find("project-1")).thenReturn(Optional.of(new ModelDefaultPolicySnapshot(
                1L,
                new ModelDefaultPolicy("project-1", "model-1", "", "", "", ModelPolicyStatus.ENABLED),
                null,
                null)));
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .projectId("project-1")
                .events(new ArrayList<>())
                .build();
        OpsRuntimeModelResolver resolver = new OpsRuntimeModelResolver(
                () -> mock(ChatModel.class),
                () -> mock(ChatModel.class),
                () -> modelRepository,
                () -> apiRepository,
                () -> policies,
                mock(ModelAvailabilityPort.class),
                secretResolver("resolved-key"),
                OpsRuntimeModelSettings.forTest(5, 45));

        ChatModel resolved = resolver.resolve(context);

        assertInstanceOf(OpenAiChatModel.class, resolved);
        assertEquals("model-1", context.getModelId());
        assertEquals("PROJECT_DEFAULT", context.getMetadata().get("modelSelectionSource"));
        assertEquals("model-1", context.getMetadata().get("defaultModelId"));
    }

    @Test
    void noDefaultModelMustRemainFailClosed() {
        OpsRuntimeModelResolver resolver = resolver(
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                mock(ModelAvailabilityPort.class),
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
            java.util.function.Supplier<AiClientModelCatalogPort> modelRepository,
            java.util.function.Supplier<AiClientApiCatalogPort> apiRepository,
            ModelAvailabilityPort availability,
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
