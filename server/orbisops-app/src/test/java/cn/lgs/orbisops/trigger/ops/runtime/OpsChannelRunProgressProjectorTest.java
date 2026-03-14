package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelRunProgressApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelRunProgressProjectorTest {

    private ChannelRunProgressApplicationService progress;
    private OpsChannelRunProgressProjector projector;

    @BeforeEach
    void setUp() {
        progress = mock(ChannelRunProgressApplicationService.class);
        projector = new OpsChannelRunProgressProjector(progress, Runnable::run);
    }

    @Test
    void nonChannelAndTextDeltaEventsAreIgnored() {
        projector.project(request("WEB"), event("RUN_STARTED", "Running", ""));
        projector.project(request("CHANNEL"), event("TEXT_DELTA", "delta", "secret content"));

        verify(progress, never()).project(any());
    }

    @Test
    void acceptedChannelRunProjectsPreparingWithStableCorrelation() {
        projector.project(request("CHANNEL"), event("RUN_ACCEPTED", "Task accepted", ""));

        ArgumentCaptor<ChannelRunProgressApplicationService.ProgressCommand> command =
                ArgumentCaptor.forClass(ChannelRunProgressApplicationService.ProgressCommand.class);
        verify(progress).project(command.capture());
        assertEquals("PREPARING", command.getValue().stage());
        assertTrue(command.getValue().force());
        assertEquals("channel-1", command.getValue().channelId());
        assertEquals("room-1", command.getValue().target());
        assertEquals("inbound-1", command.getValue().replyToMessageId());
    }

    @Test
    void toolEventProjectsDebouncedInvestigationWithoutPayloadDetails() {
        OpsRuntimeEvent event = OpsRuntimeEvent.builder()
                .eventType("TOOL_CALL_STARTED")
                .status("RUNNING")
                .summary("Tool call started: mysql_query")
                .payload(Map.of("arguments", "password=must-not-project"))
                .build();

        projector.project(request("CHANNEL"), event);

        ArgumentCaptor<ChannelRunProgressApplicationService.ProgressCommand> command =
                ArgumentCaptor.forClass(ChannelRunProgressApplicationService.ProgressCommand.class);
        verify(progress).project(command.capture());
        assertEquals("INVESTIGATING", command.getValue().stage());
        assertFalse(command.getValue().force());
        assertFalse(command.getValue().summary().contains("must-not-project"));
    }

    @Test
    void approvalAndFinalOutputAreForcedTransitionsButFinalContentIsNotProjected() {
        projector.project(request("CHANNEL"), event("WORKFLOW_APPROVAL_WAITING", "Approval required", ""));
        projector.project(request("CHANNEL"), event("FINAL_OUTPUT", "Agent output complete", "full private answer"));

        ArgumentCaptor<ChannelRunProgressApplicationService.ProgressCommand> command =
                ArgumentCaptor.forClass(ChannelRunProgressApplicationService.ProgressCommand.class);
        verify(progress, org.mockito.Mockito.times(2)).project(command.capture());
        var values = command.getAllValues();
        assertEquals("WAITING_APPROVAL", values.get(0).stage());
        assertTrue(values.get(0).force());
        assertEquals("COMPLETED", values.get(1).stage());
        assertTrue(values.get(1).force());
        assertEquals("Agent output complete", values.get(1).summary());
        assertFalse(values.get(1).summary().contains("private answer"));
    }

    @Test
    void presentationFailureNeverEscapesIntoRuntime() {
        when(progress.project(any())).thenThrow(new IllegalStateException("provider unavailable"));

        assertDoesNotThrow(() -> projector.project(
                request("CHANNEL"), event("RUN_ACCEPTED", "Task accepted", "")));
    }

    private OpsAgentChatRequest request(String source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source);
        metadata.put("channelId", "channel-1");
        metadata.put("externalConversationId", "room-1");
        metadata.put("externalMessageId", "inbound-1");
        return OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .userId("user-1")
                .query("why is checkout failing")
                .metadata(metadata)
                .build();
    }

    private OpsRuntimeEvent event(String type, String summary, String content) {
        return OpsRuntimeEvent.builder()
                .eventType(type)
                .status("RUNNING")
                .summary(summary)
                .content(content)
                .build();
    }
}
