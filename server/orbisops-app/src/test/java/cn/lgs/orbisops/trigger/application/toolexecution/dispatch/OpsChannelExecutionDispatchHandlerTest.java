package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelExecutionDispatchHandlerTest {

    @Test
    void shouldMapProjectBoundSendCommand() {
        ChannelQueryService channels = mock(ChannelQueryService.class);
        ChannelChatProcessManager chat = mock(ChannelChatProcessManager.class);
        when(chat.send(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("status", "SENT"));
        OpsChannelExecutionDispatchHandler handler = new OpsChannelExecutionDispatchHandler(channels, chat);

        Object output = handler.dispatch(target("channel_send"), request(Map.of(
                "channelId", "channel-1", "target", "room-1", "content", "hello")));

        assertEquals("SENT", ((Map<?, ?>) output).get("status"));
        ArgumentCaptor<ChannelModels.Send> command = ArgumentCaptor.forClass(ChannelModels.Send.class);
        verify(chat).send(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("alice", command.getValue().actor());
        assertEquals("run-1", command.getValue().metadata().get("runId"));
        assertEquals("AGENT_TOOL", command.getValue().metadata().get("source"));
    }

    @Test
    void missingProjectMustFailClosed() {
        OpsChannelExecutionDispatchHandler handler = new OpsChannelExecutionDispatchHandler(
                mock(ChannelQueryService.class), mock(ChannelChatProcessManager.class));
        ToolExecutionRequest request = new ToolExecutionRequest(
                "", "alice", "alice", "channel.notification", "channel_list",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, Map.of(),
                "session-1", "run-1", Map.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> handler.dispatch(target("channel_list"), request));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "channel.notification", toolName, "CHANNEL", "MEDIUM",
                false, false, false, false, false);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "channel.notification", "channel_send",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1",
                Map.of("projectId", "project-1", "runId", "run-1", "sessionId", "session-1"), Map.of());
    }
}
