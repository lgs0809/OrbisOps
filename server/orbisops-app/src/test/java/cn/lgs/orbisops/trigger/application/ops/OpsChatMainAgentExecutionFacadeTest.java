package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsActionStatus;
import cn.lgs.orbisops.application.agent.OpsFailureCategory;
import cn.lgs.orbisops.application.agent.OpsFailureDescriptor;
import cn.lgs.orbisops.application.agent.OpsMainAgentActionType;
import cn.lgs.orbisops.application.agent.OpsMainAgentCommand;
import cn.lgs.orbisops.application.agent.OpsMainAgentCoordinator;
import cn.lgs.orbisops.application.agent.OpsMainAgentOutcome;
import cn.lgs.orbisops.application.agent.OpsSideEffectState;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChatMainAgentExecutionFacadeTest {

    @Test
    void successfulResponseUsesSingleAgentActionAndModelToolSelection() {
        OpsMainAgentCoordinator coordinator = mock(OpsMainAgentCoordinator.class);
        OpsAgentChatResponse raw = OpsAgentChatResponse.builder()
                .content("ok")
                .metadata(Map.of("existing", true))
                .build();
        when(coordinator.execute(any())).thenReturn(
                OpsMainAgentOutcome.succeeded(raw, Map.of("traceId", "trace-1")));
        OpsChatMainAgentExecutionFacade facade = new OpsChatMainAgentExecutionFacade(coordinator);
        OpsAgentChatRequest request = request();

        OpsAgentChatResponse response = facade.execute(request, null, true);

        assertSame(raw, response);
        assertEquals("AGENT", response.getMetadata().get("mainAgentAction"));
        assertEquals("SUCCEEDED", response.getMetadata().get("mainAgentStatus"));
        assertEquals("trace-1", response.getMetadata().get("traceId"));
        ArgumentCaptor<OpsMainAgentCommand> captor = ArgumentCaptor.forClass(OpsMainAgentCommand.class);
        verify(coordinator).execute(captor.capture());
        OpsMainAgentCommand command = captor.getValue();
        assertEquals(OpsMainAgentActionType.AGENT, command.actionType());
        assertEquals("MODEL_TOOL_SELECTION", command.attributes().get("routing"));
        OpsChatActionPayload payload = assertInstanceOf(OpsChatActionPayload.class, command.payload());
        assertSame(request, payload.request());
        assertTrue(payload.synchronous());
    }

    @Test
    void invalidSuccessfulResultFailsClosed() {
        OpsMainAgentCoordinator coordinator = mock(OpsMainAgentCoordinator.class);
        when(coordinator.execute(any())).thenReturn(OpsMainAgentOutcome.succeeded("unexpected", Map.of()));
        OpsChatMainAgentExecutionFacade facade = new OpsChatMainAgentExecutionFacade(coordinator);
        List<OpsRuntimeEvent> emitted = new ArrayList<>();

        OpsAgentChatResponse response = facade.execute(request(), emitted::add, false);

        assertEquals("FAILED", response.getMetadata().get("status"));
        assertEquals("MAIN_AGENT_HANDLER_RESULT_INVALID", response.getMetadata().get("reasonCode"));
        assertEquals(1, emitted.size());
    }

    @Test
    void typedFailureDescriptorMapsToSafeResponse() {
        OpsMainAgentCoordinator coordinator = mock(OpsMainAgentCoordinator.class);
        OpsFailureDescriptor failure = new OpsFailureDescriptor(
                "EVIDENCE_REQUIRED", "INPUT_VALIDATION", OpsFailureCategory.VALIDATION,
                "请补充证据。", false, OpsSideEffectState.NOT_STARTED,
                List.of("evidence"), List.of("ADD_EVIDENCE"), Map.of("field", "evidence"));
        when(coordinator.execute(any())).thenReturn(
                OpsMainAgentOutcome.failed(OpsActionStatus.NEEDS_INPUT, failure));
        OpsChatMainAgentExecutionFacade facade = new OpsChatMainAgentExecutionFacade(coordinator);

        OpsAgentChatResponse response = facade.execute(request(), null, true);

        assertEquals("请补充证据。", response.getContent());
        assertEquals("NEEDS_INPUT", response.getMetadata().get("status"));
        assertEquals("EVIDENCE_REQUIRED", response.getMetadata().get("reasonCode"));
        assertEquals(List.of("ADD_EVIDENCE"), response.getMetadata().get("recoveryActions"));
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .userId("alice")
                .agentDefinitionId("agent-1")
                .query("hello")
                .build();
    }
}
