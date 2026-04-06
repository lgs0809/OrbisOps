package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsRuntimeConversationContextCoordinatorTest {

    @Test
    void prepareMainQuestionDelegatesWithoutDegradingFailures() {
        OpsWorkSessionContextPreparationService preparationService =
                mock(OpsWorkSessionContextPreparationService.class);
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                preparationService, mock(OpsConversationMemoryService.class));
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("agent-1").build();
        OpsAgentChatRequest request = request("原始问题");
        OpsRuntimeExecutionPlan plan = OpsRuntimeExecutionPlan.builder().memoryEnabled(true).build();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        coordinator.prepareMainQuestion(
                definition, request, plan, events, null, 123L);

        verify(preparationService).prepare(
                definition, request, plan, events, null, 123L);
    }

    @Test
    void preparedMemoryAndOriginalQuestionUseCanonicalMetadataKeys() {
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class),
                mock(OpsConversationMemoryService.class));
        OpsAgentChatRequest request = request("重写后的问题");
        request.getMetadata().put(
                OpsWorkSessionContextPreparationService.RUNTIME_MEMORY_CONTEXT_KEY,
                "历史上下文");
        request.getMetadata().put(
                OpsWorkSessionContextPreparationService.ORIGINAL_USER_QUERY_KEY,
                "原始问题");

        assertTrue(coordinator.hasMemoryContext(request));
        assertEquals("历史上下文", coordinator.memoryContext(request));
        assertEquals("原始问题", coordinator.originalUserQuery(request));
        assertEquals("原始问题", coordinator.userMemoryContent(request));
    }

    @Test
    void modelResolutionIsAuditedSeparatelyFromTheUserAuthoredMessage() {
        OpsConversationMemoryService memory = mock(OpsConversationMemoryService.class);
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class), memory);
        OpsAgentChatRequest request = request("模型补全后的查询");
        request.getMetadata().put(OpsWorkSessionContextPreparationService.ORIGINAL_USER_QUERY_KEY, "再查一下它");
        coordinator.appendMessage(request, null, new ArrayList<>(), null, 0L,
                "user", coordinator.userMemoryContent(request), Map.of());
        verify(memory).appendMessage("session-1", "user-1", "user", "再查一下它",
                Map.of("projectId", "", "runId", "", "turnId", "",
                        "originalUserQuery", "再查一下它", "resolvedQuery", "模型补全后的查询"));
    }

    @Test
    void originalQuestionFallsBackToCurrentQueryAndBlankContextRemainsPrepared() {
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class),
                mock(OpsConversationMemoryService.class));
        OpsAgentChatRequest request = request("当前问题");
        request.getMetadata().put(
                OpsWorkSessionContextPreparationService.RUNTIME_MEMORY_CONTEXT_KEY,
                "");

        assertTrue(coordinator.hasMemoryContext(request));
        assertEquals("", coordinator.memoryContext(request));
        assertEquals("当前问题", coordinator.originalUserQuery(request));
        assertEquals("当前问题", coordinator.userMemoryContent(request));
    }

    @Test
    void appendMessageDelegatesCanonicalConversationCoordinates() {
        OpsConversationMemoryService memoryService = mock(OpsConversationMemoryService.class);
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class), memoryService);
        OpsAgentChatRequest request = request("问题");
        Map<String, Object> metadata = Map.of("agentId", "agent-1");

        coordinator.appendMessage(
                request,
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                new ArrayList<>(),
                null,
                100L,
                "assistant",
                "回答",
                metadata);

        verify(memoryService).appendMessage(
                "session-1", "user-1", "assistant", "回答", Map.of("agentId", "agent-1", "projectId", "", "runId", "", "turnId", ""));
    }

    @Test
    void appendFailureRecordsDegradedEventAndDoesNotEscape() {
        OpsConversationMemoryService memoryService = mock(OpsConversationMemoryService.class);
        doThrow(new IllegalStateException("memory unavailable"))
                .when(memoryService).appendMessage(any(), any(), any(), any(), any());
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class), memoryService);
        List<OpsRuntimeEvent> events = new ArrayList<>();

        coordinator.appendMessage(
                request("问题"),
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                events,
                null,
                System.nanoTime(),
                "user",
                "问题",
                null);

        assertEquals(1, events.size());
        OpsRuntimeEvent event = events.get(0);
        assertEquals("MEMORY_APPEND_DEGRADED", event.getEventType());
        assertEquals("DEGRADED", event.getStatus());
        assertTrue(event.getSummary().contains("memory unavailable"));
        assertEquals("agent-1", event.getPayload().get("agentId"));
        assertEquals("user", event.getPayload().get("role"));
    }

    @Test
    void nullRequestHasNoPreparedContextOrQuery() {
        OpsRuntimeConversationContextCoordinator coordinator = coordinator(
                mock(OpsWorkSessionContextPreparationService.class),
                mock(OpsConversationMemoryService.class));

        assertFalse(coordinator.hasMemoryContext(null));
        assertEquals("", coordinator.memoryContext(null));
        assertEquals("", coordinator.originalUserQuery(null));
        assertEquals("", coordinator.userMemoryContent(null));
    }

    @Test
    void runtimeCannotReclaimConversationContextProtocolOrDuplicateKeys() {
        Set<String> forbiddenMethods = Set.of(
                "prepareMainQuestion",
                "runtimeMemoryContext",
                "hasRuntimeMemoryContext",
                "originalUserQuery",
                "userMemoryContent",
                "appendMemoryMessage");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> declaredFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(declaredFields.contains("RUNTIME_MEMORY_CONTEXT_KEY"));
        assertFalse(declaredFields.contains("ORIGINAL_USER_QUERY_KEY"));
        assertFalse(declaredFields.contains("memoryService"));
        assertFalse(declaredFields.contains("contextPreparationService"));
    }

    private OpsRuntimeConversationContextCoordinator coordinator(
            OpsWorkSessionContextPreparationService preparationService,
            OpsConversationMemoryService memoryService) {
        OpsRuntimeEventJournal eventJournal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        return new OpsRuntimeConversationContextCoordinator(
                preparationService, memoryService, eventJournal);
    }

    private OpsAgentChatRequest request(String query) {
        return OpsAgentChatRequest.builder()
                .sessionId("session-1")
                .userId("user-1")
                .query(query)
                .metadata(new HashMap<>())
                .build();
    }
}
