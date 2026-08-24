package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeSkillResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsLlmJsonCallOrchestratorTest {

    @Test
    void validInitialCallReturnsImmediately() {
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(null);
        ChatModel model = mock(ChatModel.class);
        List<Call> calls = new ArrayList<>();

        OpsLlmJsonCallOrchestrator.Result result = orchestrator.execute(
                input(model, List.of(), false, false, false),
                caller(calls, List.of("{\"status\":\"ok\"}")));

        assertTrue(result.success());
        assertEquals("ok", result.json().getString("status"));
        assertEquals(1, calls.size());
        assertEquals("planner", calls.get(0).agentName());
        assertTrue(calls.get(0).enableSkillTool());
    }

    @Test
    void eagerInitialContextRecordsExplicitSkillActivationWithoutToolRoundTrip() {
        var fixture = new OpsLlmFrozenSkillTestFixture("rag-knowledge-agent", "body");
        OpsRuntimeSkillResolver provider = fixture.resolver;
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(provider);
        ChatModel model = mock(ChatModel.class);
        List<Call> calls = new ArrayList<>();
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                events, null, "owner", "node-rag", "AGENT", "rag-agent", "rag", fixture.frame);

        OpsLlmJsonCallOrchestrator.Result result = OpsLlmTraceContext.withTrace(
                trace,
                () -> orchestrator.executeWithEagerSkillContext(
                        input(model, List.of("rag-knowledge-agent"), true, true, false),
                        caller(calls, List.of("{\"status\":\"ok\"}"))));

        assertTrue(result.success());
        assertEquals(1, calls.size());
        assertFalse(calls.get(0).enableSkillTool());
        assertTrue(calls.get(0).systemPrompt().contains("rag-knowledge-agent"));
        assertEquals(1, events.size());
        assertEquals("SKILL_CONTEXT_LOADED", events.get(0).getEventType());
        assertEquals("rag-knowledge-agent", events.get(0).getAgent());
        assertEquals("rag-knowledge-agent", ((Map<?,?>) events.get(0).getPayload().get("skillRef")).get("skillId"));
        assertEquals("EAGER", events.get(0).getPayload().get("mode"));
    }

    @Test
    void missingJsonRetriesWithEagerBoundSkillContext() {
        var fixture = new OpsLlmFrozenSkillTestFixture("skill-a", "body");
        OpsRuntimeSkillResolver provider = fixture.resolver;
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(provider);
        ChatModel model = mock(ChatModel.class);
        List<Call> calls = new ArrayList<>();

        OpsLlmJsonCallOrchestrator.Result result = fixture.run(() -> orchestrator.execute(
                input(model, List.of("skill-a"), true, true, false),
                caller(calls, List.of(
                        "plain response",
                        "{\"status\":\"eager-ok\"}"))));

        assertTrue(result.success());
        assertEquals("eager-ok", result.json().getString("status"));
        assertEquals(2, calls.size());
        assertTrue(calls.get(0).systemPrompt().contains("本次受治理 Skill 上下文"));
        assertTrue(calls.get(0).enableSkillTool());
        assertEquals("planner-skill-context-retry", calls.get(1).agentName());
        assertTrue(calls.get(1).systemPrompt().contains("本次受治理 Skill 上下文"));
        assertFalse(calls.get(1).enableSkillTool());
        assertEquals(List.of("skill-a"), calls.get(1).skillNames());
    }

    @Test
    void repairRetryUsesInitialOutputAndLatestFailureReason() {
        var fixture = new OpsLlmFrozenSkillTestFixture("skill-a", "body");
        OpsRuntimeSkillResolver provider = fixture.resolver;
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(provider);
        ChatModel model = mock(ChatModel.class);
        List<Call> calls = new ArrayList<>();

        OpsLlmJsonCallOrchestrator.Result result = fixture.run(() -> orchestrator.execute(
                input(model, List.of("skill-a"), true, true, true),
                caller(calls, List.of(
                        "INITIAL_OUTPUT",
                        "EAGER_OUTPUT",
                        "{\"status\":\"repair-ok\"}"))));

        assertTrue(result.success());
        assertEquals(3, calls.size());
        Call repair = calls.get(2);
        assertEquals("planner-json-repair", repair.agentName());
        assertTrue(repair.systemPrompt().contains("JSON 自修复器"));
        assertTrue(repair.userPrompt().contains("INITIAL_OUTPUT"));
        assertTrue(repair.userPrompt().contains("EAGER_OUTPUT"));
        assertTrue(repair.skillNames().isEmpty());
        assertFalse(repair.enableSkillTool());
    }

    @Test
    void finalFailureReturnsLatestParseReasonAndException() {
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(null);
        ChatModel model = mock(ChatModel.class);
        List<Call> calls = new ArrayList<>();

        OpsLlmJsonCallOrchestrator.Result result = orchestrator.execute(
                input(model, List.of(), false, false, true),
                caller(calls, List.of("plain", "{invalid-json}")));

        assertFalse(result.success());
        assertTrue(result.reason().startsWith("JSON 解析失败："));
        assertNotNull(result.exception());
        assertEquals(2, calls.size());
    }

    @Test
    void everyInvalidStageRecordsRuntimeEventInOrder() {
        var fixture = new OpsLlmFrozenSkillTestFixture("skill-a", "body");
        OpsRuntimeSkillResolver provider = fixture.resolver;
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(provider);
        ChatModel model = mock(ChatModel.class);
        List<OpsRuntimeEvent> events = new ArrayList<>();
        OpsLlmTraceContext.Trace trace = new OpsLlmTraceContext.Trace(
                events,
                null,
                "owner",
                "node-1",
                "AGENT",
                "planner",
                "llm", fixture.frame);

        OpsLlmTraceContext.withTrace(trace, () -> orchestrator.execute(
                input(model, List.of("skill-a"), true, true, true),
                caller(new ArrayList<>(), List.of(
                        "initial invalid",
                        "eager invalid",
                        "repair invalid"))));

        events.removeIf(event -> "SKILL_CONTEXT_LOADED".equals(event.getEventType()));
        assertEquals(3, events.size());
        assertEquals("MODEL_JSON_INVALID", events.get(0).getEventType());
        assertEquals("planner", events.get(0).getPayload().get("agentName"));
        assertEquals("planner-skill-context-retry",
                events.get(1).getPayload().get("agentName"));
        assertEquals("planner-json-repair",
                events.get(2).getPayload().get("agentName"));
    }

    @Test
    void callerExceptionPropagatesWithoutOrchestratorDegradation() {
        OpsLlmJsonCallOrchestrator orchestrator = orchestrator(null);
        ChatModel model = mock(ChatModel.class);
        OpsLlmDegradationException failure =
                new OpsLlmDegradationException("planner", "failed");

        OpsLlmDegradationException thrown = assertThrows(
                OpsLlmDegradationException.class,
                () -> orchestrator.execute(
                        input(model, List.of(), false, false, false),
                        (agentName,
                         chatModel,
                         systemPrompt,
                         userPrompt,
                         skillNames,
                         enableSkillTool) -> {
                            throw failure;
                        }));

        assertSame(failure, thrown);
    }

    private OpsLlmJsonCallOrchestrator orchestrator(
            OpsRuntimeSkillResolver provider) {
        return new OpsLlmJsonCallOrchestrator(
                new OpsLlmJsonProtocol(),
                new OpsLlmSkillContextService(() -> provider),
                new OpsLlmObservabilityService());
    }

    private OpsLlmJsonCallOrchestrator.Input input(
            ChatModel model,
            Collection<String> skillNames,
            boolean skillContextEnabled,
            boolean skillRetryEnabled,
            boolean repairRetryEnabled) {
        return new OpsLlmJsonCallOrchestrator.Input(
                "planner",
                model,
                "system",
                "user",
                skillNames,
                6000,
                skillContextEnabled,
                1200,
                skillRetryEnabled,
                500,
                repairRetryEnabled);
    }

    private OpsLlmJsonCallOrchestrator.ContentCaller caller(
            List<Call> calls,
            List<String> outputs) {
        int[] index = {0};
        return (agentName,
                chatModel,
                systemPrompt,
                userPrompt,
                skillNames,
                enableSkillTool) -> {
            calls.add(new Call(
                    agentName,
                    systemPrompt,
                    userPrompt,
                    skillNames == null ? List.of() : List.copyOf(skillNames),
                    enableSkillTool));
            return outputs.get(index[0]++);
        };
    }

    private record Call(
            String agentName,
            String systemPrompt,
            String userPrompt,
            List<String> skillNames,
            boolean enableSkillTool) {
    }
}
