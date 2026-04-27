package cn.lgs.orbisops.application.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelNotificationUseCaseTest {

    @Test
    void disabledNotificationDoesNotTouchOutbox() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);

        ChannelNotificationOutcome outcome = useCase.notifyIfNeeded(analysis(false));

        assertFalse(outcome.attempted());
        assertFalse(outcome.success());
        assertFalse(outcome.appendExecutionNote());
        verify(delivery, never()).available();
    }

    @Test
    void unavailableStoreReportsAttemptWithoutExecutionNote() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);
        when(delivery.available()).thenReturn(false);

        ChannelNotificationOutcome outcome = useCase.notifyIfNeeded(analysis(true));

        assertTrue(outcome.attempted());
        assertFalse(outcome.success());
        assertFalse(outcome.appendExecutionNote());
        assertTrue(outcome.message().contains("存储不可用"));
        verify(delivery, never()).enqueueAnalysis(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulDispatchPreservesDispatcherExecutionNoteDecision() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);
        ChannelAnalysisNotificationCommand command = analysis(true);
        when(delivery.available()).thenReturn(true);
        when(delivery.enqueueAnalysis(command)).thenReturn(7L);
        when(delivery.dispatch(7L)).thenReturn(new ChannelDispatchOutcome(
                true, "Channel 通知已投递：channel-1", true));

        ChannelNotificationOutcome outcome = useCase.notifyIfNeeded(command);

        assertTrue(outcome.success());
        assertTrue(outcome.appendExecutionNote());
        assertEquals("Channel 通知已投递：channel-1", outcome.message());
    }

    @Test
    void failedDispatchAlwaysAddsExecutionNote() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);
        ChannelAnalysisNotificationCommand command = analysis(true);
        when(delivery.available()).thenReturn(true);
        when(delivery.enqueueAnalysis(command)).thenReturn(7L);
        when(delivery.dispatch(7L)).thenReturn(new ChannelDispatchOutcome(
                false, "Channel 通知正在处理或尚未到重试时间。", false));

        ChannelNotificationOutcome outcome = useCase.notifyIfNeeded(command);

        assertFalse(outcome.success());
        assertTrue(outcome.appendExecutionNote());
    }

    @Test
    void enqueueOrDispatchFailureIsSanitizedAndConvertedToOutcome() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);
        ChannelAnalysisNotificationCommand command = analysis(true);
        when(delivery.available()).thenReturn(true);
        when(delivery.enqueueAnalysis(command)).thenThrow(new IllegalStateException("token=raw"));
        when(delivery.sanitizeFailure("token=raw")).thenReturn("token=***");

        ChannelNotificationOutcome outcome = useCase.notifyIfNeeded(command);

        assertTrue(outcome.attempted());
        assertFalse(outcome.success());
        assertTrue(outcome.appendExecutionNote());
        assertEquals("Channel 通知失败：token=***", outcome.message());
    }

    @Test
    void replyPersistsThenDispatchesThenReadsTerminalStatus() {
        ChannelNotificationDeliveryPort delivery = mock(ChannelNotificationDeliveryPort.class);
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(delivery);
        ChannelChatReplyCommand command = new ChannelChatReplyCommand(
                "project-1", "channel-1", "room-1", "reply", "run-1", "session-1", "message-1");
        when(delivery.enqueueReply(command)).thenReturn(9L);
        when(delivery.dispatch(9L)).thenReturn(new ChannelDispatchOutcome(false, "failed", false));
        when(delivery.statusOf(9L)).thenReturn("DEAD_LETTER");

        ChannelReplyDeliveryOutcome outcome = useCase.enqueueChatReply(command);

        assertEquals(9L, outcome.outboxId());
        assertFalse(outcome.delivered());
        assertTrue(outcome.terminalFailure());
        verify(delivery).enqueueReply(command);
        verify(delivery).dispatch(9L);
        verify(delivery).statusOf(9L);
    }

    @Test
    void nullReplyCommandIsRejectedBeforeDelivery() {
        ChannelNotificationUseCase useCase = new ChannelNotificationUseCase(
                mock(ChannelNotificationDeliveryPort.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.enqueueChatReply(null));

        assertEquals("CHANNEL_CHAT_REPLY_COMMAND_REQUIRED", error.getMessage());
    }

    private ChannelAnalysisNotificationCommand analysis(boolean requested) {
        return new ChannelAnalysisNotificationCommand(
                requested,
                "project-1",
                "channel-1",
                "room-1",
                "run-1",
                "analysis-1",
                "2026-07-30 09:00:00",
                "# report");
    }
}
