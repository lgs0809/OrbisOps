package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.channel.ChannelAnalysisNotificationCommand;
import cn.lgs.orbisops.application.channel.ChannelChatReplyCommand;
import cn.lgs.orbisops.application.channel.ChannelNotificationOutcome;
import cn.lgs.orbisops.application.channel.ChannelNotificationUseCase;
import cn.lgs.orbisops.application.channel.ChannelReplyDeliveryOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelNotificationServiceTest {

    @Test
    void analysisNotificationProjectsDtosAndAppendsRequestedExecutionNote() {
        ChannelNotificationUseCase useCase = mock(ChannelNotificationUseCase.class);
        OpsChannelNotificationService service = service(useCase);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .projectId("project-1")
                .notifyChannel(true)
                .notificationChannelId("channel-1")
                .notificationTarget("room-1")
                .runId("run-1")
                .build();
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .analysisId("analysis-1")
                .generatedAt("2026-07-30 09:00:00")
                .markdownReport("# report")
                .executionNotes(new ArrayList<>())
                .build();
        when(useCase.notifyIfNeeded(any())).thenReturn(new ChannelNotificationOutcome(
                true, false, "delivery failed", true));

        OpsChannelNotificationService.NotifyResult result = service.notifyIfNeeded(request, response);

        ArgumentCaptor<ChannelAnalysisNotificationCommand> command =
                ArgumentCaptor.forClass(ChannelAnalysisNotificationCommand.class);
        verify(useCase).notifyIfNeeded(command.capture());
        assertTrue(command.getValue().requested());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("channel-1", command.getValue().channelId());
        assertEquals("analysis-1", command.getValue().analysisId());
        assertEquals("# report", command.getValue().markdownReport());
        assertTrue(result.attempted());
        assertFalse(result.success());
        assertEquals(List.of("delivery failed"), response.getExecutionNotes());
    }

    @Test
    void analysisNotificationDoesNotAppendWhenUseCaseDeclinesNote() {
        ChannelNotificationUseCase useCase = mock(ChannelNotificationUseCase.class);
        OpsChannelNotificationService service = service(useCase);
        OpsAnalysisResponseDTO response = OpsAnalysisResponseDTO.builder()
                .executionNotes(new ArrayList<>())
                .build();
        when(useCase.notifyIfNeeded(any())).thenReturn(new ChannelNotificationOutcome(
                true, false, "store unavailable", false));

        service.notifyIfNeeded(OpsAgentRunRequestDTO.builder().notifyChannel(true).build(), response);

        assertTrue(response.getExecutionNotes().isEmpty());
    }

    @Test
    void chatReplyProjectsCommandAndCompatibilityDelivery() {
        ChannelNotificationUseCase useCase = mock(ChannelNotificationUseCase.class);
        OpsChannelNotificationService service = service(useCase);
        when(useCase.enqueueChatReply(any())).thenReturn(new ChannelReplyDeliveryOutcome(
                9L, false, "DEAD_LETTER", "failed"));

        OpsChannelNotificationService.ReplyDelivery result = service.enqueueChatReply(
                "project-1", "channel-1", "room-1", "reply",
                "run-1", "session-1", "message-1");

        ArgumentCaptor<ChannelChatReplyCommand> command =
                ArgumentCaptor.forClass(ChannelChatReplyCommand.class);
        verify(useCase).enqueueChatReply(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("message-1", command.getValue().replyToMessageId());
        assertEquals(9L, result.outboxId());
        assertTrue(result.terminalFailure());
        assertFalse(new OpsChannelNotificationService.ReplyDelivery(
                4L, false, "FAILED", "retry").terminalFailure());
    }

    @Test
    void administrationMethodsRemainPureDelegation() {
        ChannelNotificationUseCase useCase = mock(ChannelNotificationUseCase.class);
        OpsChannelOutboxQueryService query = mock(OpsChannelOutboxQueryService.class);
        OpsChannelOutboxManagementService management = mock(OpsChannelOutboxManagementService.class);
        OpsChannelOutboxBatchProcessor batch = mock(OpsChannelOutboxBatchProcessor.class);
        OpsChannelNotificationService service = new OpsChannelNotificationService(
                useCase, query, management, batch);
        when(batch.process(20)).thenReturn(new OpsChannelOutboxBatchProcessor.ProcessingSummary(
                2, 1, 1, List.of("ok", "failed")));
        when(query.list("project-1", 10)).thenReturn(List.of(Map.of("id", 1)));
        when(query.status("project-1")).thenReturn(Map.of("ready", true));
        when(query.statusAll()).thenReturn(Map.of("ready", true));
        when(management.requeue("project-1", 7L, "admin")).thenReturn(Map.of("status", "PENDING"));
        when(management.cancel("project-1", 8L, "admin")).thenReturn(Map.of("status", "CANCELLED"));

        assertEquals(2, service.processPending(20).get("processed"));
        assertEquals(1, service.listOutbox("project-1", 10).size());
        assertEquals(true, service.status("project-1").get("ready"));
        assertEquals(true, service.statusAll().get("ready"));
        assertEquals("PENDING", service.requeueDeadLetter("project-1", 7L, "admin").get("status"));
        assertEquals("CANCELLED", service.cancelDeadLetter("project-1", 8L, "admin").get("status"));
    }

    private OpsChannelNotificationService service(ChannelNotificationUseCase useCase) {
        return new OpsChannelNotificationService(
                useCase,
                mock(OpsChannelOutboxQueryService.class),
                mock(OpsChannelOutboxManagementService.class),
                mock(OpsChannelOutboxBatchProcessor.class));
    }
}
