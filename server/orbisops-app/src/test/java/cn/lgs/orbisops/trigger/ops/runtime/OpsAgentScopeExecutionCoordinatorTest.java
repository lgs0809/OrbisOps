package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAgentScopeExecutionCoordinatorTest {

    @Test
    void delegatesExecutionAndProvidesCanonicalHooks() {
        OpsAgentScopeExecutor executor = mock(OpsAgentScopeExecutor.class);
        AtomicReference<OpsAgentChatRequest> cancellationRequest = new AtomicReference<>();
        AtomicReference<ToolLoopCoordinator> toolLoopRef =
                new AtomicReference<>(new ToolLoopCoordinator());
        OpsRuntimeConversationContextCoordinator conversationCoordinator =
                new OpsRuntimeConversationContextCoordinator(null, null, null);
        OpsAgentScopeExecutionCoordinator coordinator =
                new OpsAgentScopeExecutionCoordinator(
                        executor,
                        conversationCoordinator,
                        cancellationRequest::set,
                        toolLoopRef::get);
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("agent-1").build();
        OpsAgentChatRequest request = request();
        request.getMetadata().put(
                OpsWorkSessionContextPreparationService.RUNTIME_MEMORY_CONTEXT_KEY,
                "历史上下文");
        List<OpsRuntimeEvent> events = new ArrayList<>();
        ArgumentCaptor<OpsAgentScopeExecutor.Hooks> hooksCaptor =
                ArgumentCaptor.forClass(OpsAgentScopeExecutor.Hooks.class);
        when(executor.execute(
                eq(definition),
                eq(request),
                eq("输入"),
                eq(events),
                isNull(),
                any(OpsAgentScopeExecutor.Hooks.class)))
                .thenReturn("输出");

        String output = coordinator.execute(
                definition, request, "输入", events, null);

        assertEquals("输出", output);
        verify(executor).execute(
                eq(definition),
                eq(request),
                eq("输入"),
                eq(events),
                isNull(),
                hooksCaptor.capture());
        OpsAgentScopeExecutor.Hooks hooks = hooksCaptor.getValue();
        hooks.assertNotCanceled(request);
        assertSame(request, cancellationRequest.get());
        assertTrue(hooks.hasPreparedMemoryContext(request));
        assertEquals("历史上下文", hooks.memoryContext(request));
        assertSame(toolLoopRef.get(), hooks.toolLoopCoordinator());
    }

    @Test
    void optionalToolLoopIsResolvedAtHookCallTime() {
        OpsAgentScopeExecutor executor = mock(OpsAgentScopeExecutor.class);
        AtomicReference<ToolLoopCoordinator> toolLoopRef = new AtomicReference<>();
        OpsAgentScopeExecutionCoordinator coordinator =
                new OpsAgentScopeExecutionCoordinator(
                        executor,
                        new OpsRuntimeConversationContextCoordinator(null, null, null),
                        ignored -> {
                        },
                        toolLoopRef::get);
        ArgumentCaptor<OpsAgentScopeExecutor.Hooks> hooksCaptor =
                ArgumentCaptor.forClass(OpsAgentScopeExecutor.Hooks.class);
        when(executor.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn("");

        coordinator.execute(
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                request(),
                "输入",
                new ArrayList<>(),
                null);
        verify(executor).execute(any(), any(), any(), any(), any(), hooksCaptor.capture());

        OpsAgentScopeExecutor.Hooks hooks = hooksCaptor.getValue();
        assertNull(hooks.toolLoopCoordinator());
        ToolLoopCoordinator lateInjected = new ToolLoopCoordinator();
        toolLoopRef.set(lateInjected);
        assertSame(lateInjected, hooks.toolLoopCoordinator());
    }

    @Test
    void meaningfulTextPolicyDelegatesToExecutor() {
        OpsAgentScopeExecutor executor = mock(OpsAgentScopeExecutor.class);
        when(executor.isMeaningfulText("evidence")).thenReturn(true);
        OpsAgentScopeExecutionCoordinator coordinator =
                new OpsAgentScopeExecutionCoordinator(
                        executor,
                        new OpsRuntimeConversationContextCoordinator(null, null, null),
                        ignored -> {
                        },
                        () -> null);

        assertTrue(coordinator.isMeaningfulText("evidence"));
        verify(executor).isMeaningfulText("evidence");
    }

    @Test
    void runtimeAndGraphNodeCannotReclaimAgentScopeHookProtocol() {
        Set<String> runtimeMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> runtimeFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        Set<String> graphFields = Arrays.stream(OpsGraphNodeExecutionCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        Set<String> graphHookMethods = Arrays.stream(
                        OpsGraphNodeExecutionCoordinator.Hooks.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertFalse(runtimeMethods.contains("executeAgentScope"));
        assertFalse(runtimeMethods.contains("agentScopeHooks"));
        assertFalse(runtimeFields.contains("agentScopeExecutor"));
        assertFalse(graphFields.contains("agentScopeExecutor"));
        assertFalse(graphHookMethods.contains("agentScopeHooks"));
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
}
