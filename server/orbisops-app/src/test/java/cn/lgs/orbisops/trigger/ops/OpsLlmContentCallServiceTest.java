package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimePersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsLlmContentCallServiceTest {

    @Test
    void successfulCallRecordsStartedAndFinishedEvents() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ChatModel chatModel = mock(ChatModel.class);
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("model output")))));
            OpsLlmContentCallService service = service(executorService);
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsLlmTraceContext.Trace trace = trace(events);

            String content = OpsLlmTraceContext.withTrace(trace, () -> service.call(
                    input(chatModel, false, false)));

            assertEquals("model output", content);
            assertEquals(2, events.size());
            assertEquals("MODEL_CALL_STARTED", events.get(0).getEventType());
            assertEquals("MODEL_CALL_FINISHED", events.get(1).getEventType());
            assertEquals("SUCCEEDED", events.get(1).getStatus());
            assertEquals(Boolean.FALSE,
                    events.get(0).getPayload().get("skillToolEnabled"));
            assertEquals(Boolean.FALSE,
                    events.get(0).getPayload().get("jsonResponseFormatEnabled"));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void runtimeFailureRecordsFailedEventAndRethrowsSameException() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ChatModel chatModel = mock(ChatModel.class);
            IllegalArgumentException failure = new IllegalArgumentException("model failed");
            when(chatModel.call(any(Prompt.class))).thenThrow(failure);
            OpsLlmContentCallService service = service(executorService);
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsLlmTraceContext.Trace trace = trace(events);

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> OpsLlmTraceContext.withTrace(trace, () -> service.call(
                            input(chatModel, false, false))));

            assertSame(failure, thrown);
            assertEquals(2, events.size());
            assertEquals("MODEL_CALL_STARTED", events.get(0).getEventType());
            assertEquals("MODEL_CALL_FAILED", events.get(1).getEventType());
            assertEquals("model failed", events.get(1).getPayload().get("error"));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void completedModelWithPersistenceFailureIsNotReclassifiedAsModelFailure() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            ChatModel chatModel = mock(ChatModel.class);
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("model output")))));
            OpsLlmContentCallService service = service(executorService);
            List<OpsRuntimeEvent> events = new ArrayList<>();
            OpsRuntimePersistenceException persistenceFailure = new OpsRuntimePersistenceException(
                    "checkpoint deadlock", new IllegalStateException("deadlock"));
            OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                    events,
                    event -> {
                        if ("MODEL_CALL_FINISHED".equals(event.getEventType())) {
                            throw persistenceFailure;
                        }
                    },
                    "owner", "node-1", "AGENT", "planner", "llm");

            OpsRuntimePersistenceException thrown = assertThrows(
                    OpsRuntimePersistenceException.class,
                    () -> OpsLlmTraceContext.withTrace(trace, () -> service.call(
                            input(chatModel, false, false))));

            assertSame(persistenceFailure, thrown);
            assertEquals(2, events.size());
            assertEquals("MODEL_CALL_STARTED", events.get(0).getEventType());
            assertEquals("MODEL_CALL_FINISHED", events.get(1).getEventType());
            assertTrue(events.stream().noneMatch(event ->
                    "MODEL_CALL_FAILED".equals(event.getEventType())));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void responseOptionsRespectEnableFlagAndTokenConfiguration() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            OpsLlmContentCallService service = service(executorService);

            assertNull(service.responseOptions(false, 1200));
            assertNotNull(service.responseOptions(true, 1200));
            assertNotNull(service.responseOptions(true, 0));
        } finally {
            executorService.shutdownNow();
        }
    }

    private OpsLlmContentCallService service(ExecutorService executorService) {
        return new OpsLlmContentCallService(
                new OpsLlmModelCallExecutor(executorService),
                new OpsLlmSkillContextService(() -> null),
                new OpsLlmObservabilityService());
    }

    private OpsLlmContentCallService.Input input(
            ChatModel chatModel,
            boolean enableSkillTool,
            boolean jsonResponseFormatEnabled) {
        return new OpsLlmContentCallService.Input(
                "planner",
                chatModel,
                "system",
                "user",
                List.of(),
                enableSkillTool,
                jsonResponseFormatEnabled,
                1200,
                30);
    }

    private OpsLlmTraceContext.Trace trace(List<OpsRuntimeEvent> events) {
        return new OpsLlmTraceContext.Trace(
                events,
                null,
                "owner",
                "node-1",
                "AGENT",
                "planner",
                "llm");
    }
}
