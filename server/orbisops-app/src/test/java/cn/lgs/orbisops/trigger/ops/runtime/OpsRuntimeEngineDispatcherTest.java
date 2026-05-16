package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeEngineDispatcherTest {

    @Test
    void executePlanResolvesAdapterAndUsesDispatcherAsRuntimeSupport() {
        OpsEngineAdapter adapter = mock(OpsEngineAdapter.class);
        when(adapter.key()).thenReturn(OpsUnifiedAgentEngineAdapter.KEY);
        ArgumentCaptor<OpsAgentRuntimeSupport> supportCaptor =
                ArgumentCaptor.forClass(OpsAgentRuntimeSupport.class);
        OpsRuntimeEngineDispatcher dispatcher = dispatcher(List.of(adapter), new AtomicReference<>());
        OpsAgentDefinition definition = definition();
        OpsAgentChatRequest request = request();
        OpsRuntimeExecutionPlan plan = plan(OpsUnifiedAgentEngineAdapter.KEY);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        when(adapter.execute(
                eq(definition),
                eq(request),
                eq(plan),
                any(OpsAgentRuntimeSupport.class),
                eq(events),
                isNull()))
                .thenReturn("有效输出");

        String output = dispatcher.executePlan(
                definition, request, plan, events, null);

        assertEquals("有效输出", output);
        verify(adapter).execute(
                eq(definition),
                eq(request),
                eq(plan),
                supportCaptor.capture(),
                eq(events),
                isNull());
        assertSame(dispatcher, supportCaptor.getValue());
        assertEquals(Set.of(OpsUnifiedAgentEngineAdapter.KEY), dispatcher.adapterKeys());
    }

    @Test
    void executePlanRejectsMissingPlanAndUnknownAdapter() {
        OpsRuntimeEngineDispatcher dispatcher = dispatcher(List.of(), new AtomicReference<>());

        assertEquals("RUNTIME_EXECUTION_PLAN_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> dispatcher.executePlan(
                                definition(), request(), null, new ArrayList<>(), null))
                        .getMessage());
        assertEquals("LEGACY_TOP_LEVEL_AGENT_RUNTIME_FORBIDDEN:UNKNOWN",
                assertThrows(SecurityException.class,
                        () -> dispatcher.executePlan(
                                definition(), request(), plan("UNKNOWN"), new ArrayList<>(), null))
                        .getMessage());
        assertEquals("未注册 Agent 引擎适配器：" + OpsUnifiedAgentEngineAdapter.KEY,
                assertThrows(IllegalStateException.class,
                        () -> dispatcher.executePlan(
                                definition(), request(),
                                plan(OpsUnifiedAgentEngineAdapter.KEY),
                                new ArrayList<>(), null))
                        .getMessage());
    }

    @Test
    void lateStrategyRegistryOverridesFallbackDispatch() {
        AtomicReference<OpsNodeExecutionStrategyRegistry> registryRef = new AtomicReference<>();
        Fixture fixture = fixture(List.of(), registryRef);
        OpsNodeExecutionStrategyRegistry registry = mock(OpsNodeExecutionStrategyRegistry.class);
        registryRef.set(registry);
        when(registry.execute(eq(OpsRuntimeExecutionNode.CHAT), any()))
                .thenReturn("strategy-output");

        String output = fixture.dispatcher().runChatEngine(
                definition(), request(), plan("unused"), new ArrayList<>(), null);

        assertEquals("strategy-output", output);
        verify(registry).execute(eq(OpsRuntimeExecutionNode.CHAT), any());
        verify(fixture.chatCoordinator(), never()).execute(
                any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void fallbackDispatchesAllCanonicalEngineTypes() {
        Fixture fixture = fixture(List.of(), new AtomicReference<>());
        when(fixture.chatCoordinator().execute(
                any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn("chat-output");
        when(fixture.graphCoordinator().execute(
                any(), any(), any(), any(), any()))
                .thenReturn("graph-output");
        when(fixture.agentScopeCoordinator().execute(
                any(), any(), any(), any(), any()))
                .thenReturn("scope-output");

        assertEquals("chat-output", fixture.dispatcher().runChatEngine(
                definition(), request(), plan("unused"), new ArrayList<>(), null));
        assertEquals("graph-output", fixture.dispatcher().runGraphEngine(
                definition(), request(), plan("unused"), new ArrayList<>(), null));
        assertEquals("scope-output", fixture.dispatcher().runAgentScopeEngine(
                definition(), request(), plan("unused"), new ArrayList<>(), null));
    }

    @Test
    void runtimeCannotReclaimAdapterRegistryOrRuntimeSupportProtocol() {
        Set<String> forbiddenMethods = Set.of(
                "runChatEngine",
                "runGraphEngine",
                "runAgentScopeEngine",
                "runtimeNodeContext",
                "executeNodeStrategy",
                "executeChatStrategy",
                "executeGraphStrategy",
                "executeAgentScopeStrategy",
                "resolveAdapter",
                "adapterMap");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> declaredFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertFalse(OpsAgentRuntimeSupport.class.isAssignableFrom(OpsWorkSessionLifecycleCoordinator.class));
        assertTrue(OpsAgentRuntimeSupport.class.isAssignableFrom(OpsRuntimeEngineDispatcher.class));
        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(declaredFields.contains("engineAdapterList"));
        assertFalse(declaredFields.contains("engineAdapters"));
        assertFalse(declaredFields.contains("chatEngineExecutionCoordinator"));
        assertFalse(declaredFields.contains("graphEngineExecutionCoordinator"));
        assertFalse(declaredFields.contains("agentScopeExecutionCoordinator"));
    }

    private OpsRuntimeEngineDispatcher dispatcher(
            List<OpsEngineAdapter> adapters,
            AtomicReference<OpsNodeExecutionStrategyRegistry> registryRef) {
        return fixture(adapters, registryRef).dispatcher();
    }

    private Fixture fixture(
            List<OpsEngineAdapter> adapters,
            AtomicReference<OpsNodeExecutionStrategyRegistry> registryRef) {
        OpsChatEngineExecutionCoordinator chatCoordinator =
                mock(OpsChatEngineExecutionCoordinator.class);
        OpsGraphEngineExecutionCoordinator graphCoordinator =
                mock(OpsGraphEngineExecutionCoordinator.class);
        OpsAgentScopeExecutionCoordinator agentScopeCoordinator =
                mock(OpsAgentScopeExecutionCoordinator.class);
        OpsGraphNodeExecutionCoordinator.Hooks graphHooks =
                mock(OpsGraphNodeExecutionCoordinator.Hooks.class);
        OpsRuntimeEngineDispatcher dispatcher = new OpsRuntimeEngineDispatcher(
                adapters,
                registryRef::get,
                chatCoordinator,
                graphCoordinator,
                agentScopeCoordinator,
                ignored -> {
                },
                () -> graphHooks);
        return new Fixture(
                dispatcher,
                chatCoordinator,
                graphCoordinator,
                agentScopeCoordinator);
    }

    private OpsAgentDefinition definition() {
        return OpsAgentDefinition.builder().agentId("agent-1").build();
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .query("问题")
                .metadata(new HashMap<>())
                .build();
    }

    private OpsRuntimeExecutionPlan plan(String adapterKey) {
        return OpsRuntimeExecutionPlan.builder()
                .adapterKey(adapterKey)
                .memoryEnabled(false)
                .build();
    }

    private record Fixture(
            OpsRuntimeEngineDispatcher dispatcher,
            OpsChatEngineExecutionCoordinator chatCoordinator,
            OpsGraphEngineExecutionCoordinator graphCoordinator,
            OpsAgentScopeExecutionCoordinator agentScopeCoordinator) {
    }
}
