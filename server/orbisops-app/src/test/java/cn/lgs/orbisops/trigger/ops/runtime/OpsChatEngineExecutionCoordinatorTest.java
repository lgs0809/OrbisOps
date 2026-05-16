package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatEngineExecutionCoordinatorTest {

    @Test
    void nonStreamingChatAssemblesResourcesEnhancesPromptAndCallsModel() {
        Fixture fixture = fixture();
        when(fixture.resourceAssembler().assembleAgent(any(), any(), any(), any()))
                .thenReturn(bundle(false));
        when(fixture.llmInvoker().runtimeResourcePayload(any()))
                .thenReturn(new LinkedHashMap<>(Map.of("model", "model-1")));
        when(fixture.nodeRagService().enhancePrompt(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn("RAG 增强问题");
        when(fixture.promptAssembler().systemPrompt(any(), any(), any()))
                .thenReturn("SYSTEM");
        when(fixture.llmInvoker().call(any(), any(), any(), any(), any(), anyLong()))
                .thenReturn("模型回答");
        OpsAgentChatRequest request = request("当前问题");
        fixture.eventJournal().markRequestStarted(request, System.nanoTime());
        List<OpsRuntimeEvent> events = new ArrayList<>();
        AtomicInteger cancelChecks = new AtomicInteger();

        String output = fixture.coordinator().execute(
                definition(), request, events, null, false,
                ignored -> cancelChecks.incrementAndGet());

        assertEquals("模型回答", output);
        assertEquals(2, cancelChecks.get());
        verify(fixture.llmInvoker()).call(
                eq("SYSTEM"), eq("RAG 增强问题"), any(), eq(events), isNull(), anyLong());
        verify(fixture.llmInvoker(), never()).callStreaming(
                any(), any(), any(), any(), any(), anyLong());
        assertTrue(events.stream().anyMatch(event ->
                "RESOURCE_ASSEMBLY_STARTED".equals(event.getEventType())));
        assertTrue(events.stream().anyMatch(event ->
                "RESOURCE_ASSEMBLY_FINISHED".equals(event.getEventType())));
        assertTrue(events.stream().anyMatch(event ->
                "CHAT_STARTED".equals(event.getEventType())));
    }

    @Test
    void streamingChatInjectsPreparedMemoryBeforeRagPrompt() {
        Fixture fixture = fixture();
        when(fixture.resourceAssembler().assembleAgent(any(), any(), any(), any()))
                .thenReturn(bundle(true));
        when(fixture.llmInvoker().runtimeResourcePayload(any()))
                .thenReturn(new LinkedHashMap<>());
        when(fixture.nodeRagService().enhancePrompt(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn("检索后的当前问题");
        when(fixture.promptAssembler().systemPrompt(any(), any(), any()))
                .thenReturn("SYSTEM");
        when(fixture.llmInvoker().callStreaming(any(), any(), any(), any(), any(), anyLong()))
                .thenReturn("流式回答");
        OpsAgentChatRequest request = request("当前问题");
        request.getMetadata().put(
                OpsWorkSessionContextPreparationService.RUNTIME_MEMORY_CONTEXT_KEY,
                "历史会话事实");
        fixture.eventJournal().markRequestStarted(request, System.nanoTime());
        List<OpsRuntimeEvent> events = new ArrayList<>();
        Consumer<OpsRuntimeEvent> sink = ignored -> {
        };
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);

        String output = fixture.coordinator().execute(
                definition(), request, events, sink, true, ignored -> {
                });

        assertEquals("流式回答", output);
        verify(fixture.llmInvoker()).callStreaming(
                eq("SYSTEM"), userPromptCaptor.capture(), any(), eq(events), eq(sink), anyLong());
        assertEquals(
                "### 会话记忆\n历史会话事实\n\n### 当前问题\n检索后的当前问题",
                userPromptCaptor.getValue());
        verify(fixture.llmInvoker(), never()).call(
                any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void memoryEnabledRequiresPreparedContextKey() {
        Fixture fixture = fixture();
        when(fixture.resourceAssembler().assembleAgent(any(), any(), any(), any()))
                .thenReturn(bundle(false));
        when(fixture.llmInvoker().runtimeResourcePayload(any()))
                .thenReturn(new LinkedHashMap<>());
        OpsAgentChatRequest request = request("当前问题");
        fixture.eventJournal().markRequestStarted(request, System.nanoTime());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.coordinator().execute(
                        definition(), request, new ArrayList<>(), null, true, ignored -> {
                        }));

        assertEquals("WORK_SESSION_CONTEXT_NOT_PREPARED", error.getMessage());
        verify(fixture.nodeRagService(), never()).enhancePrompt(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(fixture.llmInvoker(), never()).call(
                any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void initialCancellationPreventsResourceAssembly() {
        Fixture fixture = fixture();
        OpsAgentChatRequest request = request("当前问题");

        assertThrows(IllegalStateException.class,
                () -> fixture.coordinator().execute(
                        definition(), request, new ArrayList<>(), null, false,
                        ignored -> {
                            throw new IllegalStateException("canceled");
                        }));

        verify(fixture.resourceAssembler(), never()).assembleAgent(
                any(), any(), any(), any());
    }

    @Test
    void resourceAssemblyFailureRemainsFailClosed() {
        Fixture fixture = fixture();
        when(fixture.resourceAssembler().assembleAgent(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("resource unavailable"));
        OpsAgentChatRequest request = request("当前问题");
        fixture.eventJournal().markRequestStarted(request, System.nanoTime());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.coordinator().execute(
                        definition(), request, new ArrayList<>(), null, false, ignored -> {
                        }));

        assertEquals("resource unavailable", error.getMessage());
        verify(fixture.llmInvoker(), never()).call(
                any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void knowledgeBaseScopeReadsBundleMetadataOnly() {
        Fixture fixture = fixture();

        assertEquals("", fixture.coordinator().knowledgeBaseScope(null));
        assertEquals("", fixture.coordinator().knowledgeBaseScope(
                OpsRuntimeResourceBundle.builder().build()));
        assertEquals("department-a", fixture.coordinator().knowledgeBaseScope(
                OpsRuntimeResourceBundle.builder()
                        .metadata(Map.of("knowledgeBaseScope", "department-a"))
                        .build()));
    }

    @Test
    void runtimeCannotReclaimChatEngineProtocolOrResourceFields() {
        Set<String> forbiddenMethods = Set.of(
                "executeChat",
                "callLlm",
                "callLlmStreaming",
                "runtimeResourcePayload",
                "bundleKnowledgeBaseScope");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> declaredFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertTrue(Set.of(
                        "nodeRagService",
                        "resourceAssembler",
                        "promptAssembler",
                        "llmInvoker",
                        "aiModelAvailability")
                .stream()
                .noneMatch(declaredFields::contains));
    }

    private Fixture fixture() {
        OpsRuntimeResourceAssembler resourceAssembler = mock(OpsRuntimeResourceAssembler.class);
        OpsNodeRagService nodeRagService = mock(OpsNodeRagService.class);
        OpsRuntimePromptAssembler promptAssembler = mock(OpsRuntimePromptAssembler.class);
        OpsRuntimeLlmInvoker llmInvoker = mock(OpsRuntimeLlmInvoker.class);
        OpsRuntimeEventJournal eventJournal = new OpsRuntimeEventJournal(
                mock(OpsWorkSessionRunAdapter.class),
                mock(GraphEventApplicationService.class),
                () -> null);
        OpsRuntimeConversationContextCoordinator conversationContextCoordinator =
                new OpsRuntimeConversationContextCoordinator(
                        mock(OpsWorkSessionContextPreparationService.class),
                        mock(OpsConversationMemoryService.class),
                        eventJournal);
        return new Fixture(
                new OpsChatEngineExecutionCoordinator(
                        resourceAssembler,
                        nodeRagService,
                        promptAssembler,
                        llmInvoker,
                        conversationContextCoordinator,
                        eventJournal),
                resourceAssembler,
                nodeRagService,
                promptAssembler,
                llmInvoker,
                eventJournal);
    }

    private OpsRuntimeResourceBundle bundle(boolean ragEnabled) {
        return OpsRuntimeResourceBundle.builder()
                .ragEnabled(ragEnabled)
                .knowledgeBaseId("kb-1")
                .projectId("project-1")
                .metadata(Map.of("knowledgeBaseScope", "project"))
                .build();
    }

    private OpsAgentDefinition definition() {
        return OpsAgentDefinition.builder().agentId("agent-1").build();
    }

    private OpsAgentChatRequest request(String query) {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .query(query)
                .metadata(new HashMap<>())
                .build();
    }

    private record Fixture(
            OpsChatEngineExecutionCoordinator coordinator,
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsNodeRagService nodeRagService,
            OpsRuntimePromptAssembler promptAssembler,
            OpsRuntimeLlmInvoker llmInvoker,
            OpsRuntimeEventJournal eventJournal) {
    }
}
