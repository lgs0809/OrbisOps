package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelOutboxDispatcherTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 27, 12, 0, 0);

    @Test
    void nullTaskReturnsOriginalMissingResultWithoutRepositoryCall() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        OpsChannelOutboxDispatcher dispatcher = dispatcher(
                repository,
                mock(ChannelChatProcessManager.class));

        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                null,
                new OpsChannelOutboxDispatcher.Settings(8, 120));

        assertFalse(result.success());
        assertFalse(result.appendExecutionNote());
        assertEquals("Channel 通知任务不存在。", result.message());
        verify(repository, never()).tryAcquireLease(anyLong(), any(), any(), anyInt());
    }

    @Test
    void leaseConflictProjectsSucceededOrStillProcessingState() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.tryAcquireLease(eq(7L), any(), any(), eq(8)))
                .thenReturn(false);
        when(repository.findById(7L))
                .thenReturn(
                        Optional.of(record("SUCCEEDED", 0, "{}", "message")),
                        Optional.of(record("PENDING", 0, "{}", "message")));
        OpsChannelOutboxDispatcher dispatcher = dispatcher(
                repository,
                mock(ChannelChatProcessManager.class));

        OpsChannelOutboxDispatcher.Result succeeded = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(8, 120));
        OpsChannelOutboxDispatcher.Result pending = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(8, 120));

        assertTrue(succeeded.success());
        assertFalse(succeeded.appendExecutionNote());
        assertEquals("Channel 通知已成功处理。", succeeded.message());
        assertFalse(pending.success());
        assertFalse(pending.appendExecutionNote());
        assertEquals(
                "Channel 通知正在处理或尚未到重试时间。",
                pending.message());
    }

    @Test
    void successfulDeliveryUsesLeaseMetadataAndMarksSucceeded() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.tryAcquireLease(
                7L,
                "lease-1",
                NOW.plusSeconds(15),
                1))
                .thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(
                record("PENDING", 0, "{\"runId\":\"run-1\"}", "message")));
        ChannelChatProcessManager channel = mock(ChannelChatProcessManager.class);
        when(channel.send(any())).thenReturn(Map.of(
                "delivered", true,
                "externalMessageId", "message-7"));
        OpsChannelOutboxDispatcher dispatcher = dispatcher(repository, channel);

        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(0, 1));

        assertTrue(result.success());
        assertTrue(result.appendExecutionNote());
        assertEquals("Channel 通知已投递：channel-1", result.message());
        ArgumentCaptor<ChannelModels.Send> command =
                ArgumentCaptor.forClass(ChannelModels.Send.class);
        verify(channel).send(command.capture());
        assertEquals("project-1", command.getValue().projectId());
        assertEquals("channel-1", command.getValue().channelId());
        assertEquals("room-1", command.getValue().target());
        assertEquals("message", command.getValue().content());
        assertEquals("run-1", command.getValue().metadata().get("runId"));
        assertEquals(7L, command.getValue().metadata().get("outboxId"));
        assertEquals("channel-notification-outbox", command.getValue().actor());
        verify(repository).markSucceeded(
                eq(7L),
                eq("lease-1"),
                any());
    }

    @Test
    void retryableFailureSanitizesErrorAndSchedulesOriginalBackoff() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.tryAcquireLease(
                7L,
                "lease-1",
                NOW.plusSeconds(120),
                8))
                .thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(
                record("PENDING", 0, "{}", "message")));
        ChannelChatProcessManager channel = mock(ChannelChatProcessManager.class);
        when(channel.send(any())).thenThrow(
                new IllegalStateException("password=[REDACTED_SECRET]"));
        OpsChannelOutboxDispatcher dispatcher = dispatcher(repository, channel);

        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(8, 120));

        assertFalse(result.success());
        assertEquals(
                "Channel 通知投递失败，已进入重试：password=***",
                result.message());
        verify(repository).markFailed(
                7L,
                "lease-1",
                1,
                NOW.plusSeconds(60),
                false,
                "password=***");
    }

    @Test
    void auditFailureAfterSendIsImmediatelyDeadLetteredAcrossCauseChain() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.tryAcquireLease(
                7L,
                "lease-1",
                NOW.plusSeconds(120),
                8))
                .thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(
                record("PENDING", 0, "{}", "message")));
        ChannelChatProcessManager channel = mock(ChannelChatProcessManager.class);
        when(channel.send(any())).thenThrow(new IllegalStateException(
                "wrapper",
                new IllegalStateException(
                        "CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND")));
        OpsChannelOutboxDispatcher dispatcher = dispatcher(repository, channel);

        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(8, 120));

        assertFalse(result.success());
        assertEquals(
                "Channel 已返回投递成功但本地审计失败，结果进入人工处理且不会自动重试。",
                result.message());
        verify(repository).markFailed(
                7L,
                "lease-1",
                1,
                NOW.plusSeconds(60),
                true,
                "wrapper");
    }

    @Test
    void maximumAttemptsAreClampedAndTerminalRetryUsesOriginalDelay() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.tryAcquireLease(
                7L,
                "lease-1",
                NOW.plusSeconds(120),
                20))
                .thenReturn(true);
        when(repository.findById(7L)).thenReturn(Optional.of(
                record("PENDING", 19, "{}", "message")));
        ChannelChatProcessManager channel = mock(ChannelChatProcessManager.class);
        when(channel.send(any())).thenReturn(Map.of("delivered", false));
        OpsChannelOutboxDispatcher dispatcher = dispatcher(repository, channel);

        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                7L,
                new OpsChannelOutboxDispatcher.Settings(50, 120));

        assertFalse(result.success());
        assertTrue(result.message().contains("已进入死信"));
        verify(repository).markFailed(
                7L,
                "lease-1",
                20,
                NOW.plusSeconds(1920),
                true,
                "CHANNEL_OUTBOUND_NOT_CONFIGURED");
    }

    private OpsChannelOutboxDispatcher dispatcher(
            IChannelOutboxRepository repository,
            ChannelChatProcessManager channel) {
        return new OpsChannelOutboxDispatcher(
                repository,
                channel,
                new ChannelOutboundContentPolicy(),
                () -> "lease-1",
                () -> NOW);
    }

    private ChannelOutboxRecord record(
            String status,
            int retryCount,
            String metadata,
            String message) {
        return new ChannelOutboxRecord(
                7L,
                "dedup-1",
                "project-1",
                "channel-1",
                "room-1",
                "analysis-1",
                status,
                message,
                metadata,
                "hash",
                retryCount,
                "",
                "",
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
