package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsLlmObservabilityServiceTest {

    private final OpsLlmObservabilityService service =
            new OpsLlmObservabilityService();

    @Test
    void nullTraceProducesNoEventsAndReturnsOriginalTool() {
        ToolCallback tool = mock(ToolCallback.class);

        service.modelStarted(
                "planner",
                "system",
                "user",
                List.of("skill-a"),
                true,
                true,
                null);

        assertSame(tool, service.traceTool(
                "planner",
                tool,
                List.of("skill-a"),
                null));
    }

    @Test
    void modelEventsPreserveOriginalRuntimeContractAndPayload() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = trace(
                events,
                "node-1",
                "ROUTER",
                "bound-agent",
                "planner-source");

        service.modelStarted(
                "fallback-agent",
                "s".repeat(4100),
                "u".repeat(4100),
                List.of(" skill-a ", "skill-a", "skill-b"),
                true,
                true,
                trace);
        service.modelCompleted(
                "fallback-agent",
                "system",
                "user",
                List.of("skill-a"),
                "o".repeat(4100),
                25L,
                true,
                true,
                trace);
        IllegalStateException failure = new IllegalStateException("failed");
        service.modelFailed(
                "fallback-agent",
                "system",
                "user",
                List.of(),
                failure,
                30L,
                false,
                false,
                trace);

        assertEquals(3, events.size());
        assertEvent(
                events.get(0),
                "MODEL_CALL_STARTED",
                "RUNNING",
                "自定义运维 LLM 调用开始。",
                "bound-agent");
        Map<String, Object> started = events.get(0).getPayload();
        assertEquals("fallback-agent", started.get("agentName"));
        assertEquals(4100, started.get("systemPromptChars"));
        assertEquals(4100, started.get("userPromptChars"));
        assertEquals(8200, started.get("promptChars"));
        assertTrue(String.valueOf(started.get("systemPrompt")).endsWith("..."));
        assertTrue(String.valueOf(started.get("userPrompt")).endsWith("..."));
        assertEquals(List.of("skill-a", "skill-b"), started.get("skillNames"));
        assertEquals(Boolean.TRUE, started.get("skillToolEnabled"));
        assertEquals(1, started.get("toolCount"));
        assertEquals(Boolean.TRUE, started.get("jsonResponseFormatEnabled"));
        assertFalse(started.containsKey("durationMs"));

        assertEvent(
                events.get(1),
                "MODEL_CALL_FINISHED",
                "SUCCEEDED",
                "自定义运维 LLM 调用完成。",
                "bound-agent");
        assertEquals(25L, events.get(1).getPayload().get("durationMs"));
        assertEquals(4100, events.get(1).getPayload().get("outputChars"));
        assertTrue(String.valueOf(events.get(1).getPayload().get("output"))
                .endsWith("..."));

        assertEvent(
                events.get(2),
                "MODEL_CALL_FAILED",
                "FAILED",
                "自定义运维 LLM 调用失败：failed",
                "bound-agent");
        assertEquals("failed", events.get(2).getPayload().get("error"));
        assertEquals(30L, events.get(2).getPayload().get("durationMs"));
    }

    @Test
    void jsonInvalidKeepsOriginalSummaryAndOutputProjection() {
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = trace(
                events,
                "node-json",
                "AGENT",
                "",
                "planner-source");

        service.modelJsonInvalid(
                "planner-agent",
                "invalid reason",
                "x".repeat(4100),
                trace);

        assertEquals(1, events.size());
        OpsRuntimeEvent event = events.get(0);
        assertEvent(
                event,
                "MODEL_JSON_INVALID",
                "FAILED",
                "自定义运维 LLM 未返回合法 JSON：invalid reason",
                "planner-agent");
        assertEquals("planner-agent", event.getPayload().get("agentName"));
        assertEquals("invalid reason", event.getPayload().get("reason"));
        assertEquals(4100, event.getPayload().get("outputChars"));
        assertTrue(String.valueOf(event.getPayload().get("output")).endsWith("..."));
    }

    @Test
    void tracedToolNormalizesSchemaAndPreservesSingleArgumentCall() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        ToolMetadata metadata = mock(ToolMetadata.class);
        when(definition.name()).thenReturn("read_skill");
        when(definition.description()).thenReturn("read skill");
        when(definition.inputSchema()).thenReturn("{\"type\":\"object\"}");
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.getToolMetadata()).thenReturn(metadata);
        when(delegate.call("{\"name\":\"skill-a\"}"))
                .thenReturn("tool result");
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = trace(
                events,
                "planner-node",
                "AGENT",
                "planner-agent",
                "planner-source");

        ToolCallback traced = service.traceTool(
                "planner",
                delegate,
                List.of(" skill-a ", "skill-a"),
                trace);
        String result = traced.call("{\"name\":\"skill-a\"}");

        assertEquals("tool result", result);
        assertEquals("read_skill", traced.getToolDefinition().name());
        assertTrue(traced.getToolDefinition().inputSchema().contains("properties"));
        assertSame(metadata, traced.getToolMetadata());
        verify(delegate).call("{\"name\":\"skill-a\"}");
        assertEquals(2, events.size());
        assertEvent(
                events.get(0),
                "TOOL_CALL_STARTED",
                "RUNNING",
                "Skill 工具调用开始：read_skill",
                "planner-agent");
        assertEvent(
                events.get(1),
                "TOOL_CALL_FINISHED",
                "SUCCEEDED",
                "Skill 工具调用完成：read_skill",
                "planner-agent");
        assertEquals("skill", events.get(1).getPayload().get("toolKind"));
        assertEquals("read_skill", events.get(1).getPayload().get("toolName"));
        assertEquals(List.of("skill-a"), events.get(1).getPayload().get("skillNames"));
        assertEquals("tool result", events.get(1).getPayload().get("output"));
    }

    @Test
    void tracedToolRecordsFailureAndRethrowsSameException() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("failing_tool");
        when(delegate.getToolDefinition()).thenReturn(definition);
        IllegalArgumentException failure = new IllegalArgumentException("tool failed");
        when(delegate.call("{}")).thenThrow(failure);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = trace(
                events,
                "tool-node",
                "AGENT",
                "tool-agent",
                "tool-source");

        ToolCallback traced = service.traceTool(
                "planner",
                delegate,
                List.of(),
                trace);
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> traced.call("{}"));

        assertSame(failure, thrown);
        assertEquals(2, events.size());
        assertEquals("TOOL_CALL_FAILED", events.get(1).getEventType());
        assertEquals("FAILED", events.get(1).getStatus());
        assertEquals("tool failed", events.get(1).getPayload().get("error"));
    }

    private OpsLlmTraceContext.Trace trace(
            List<OpsRuntimeEvent> events,
            String nodeId,
            String nodeType,
            String agent,
            String source) {
        return new OpsLlmTraceContext.Trace(
                events,
                null,
                "owner",
                nodeId,
                nodeType,
                agent,
                source);
    }

    private void assertEvent(
            OpsRuntimeEvent event,
            String eventType,
            String status,
            String summary,
            String agent) {
        assertEquals(eventType, event.getEventType());
        assertEquals(status, event.getStatus());
        assertEquals(summary, event.getSummary());
        assertEquals(agent, event.getAgent());
    }
}
