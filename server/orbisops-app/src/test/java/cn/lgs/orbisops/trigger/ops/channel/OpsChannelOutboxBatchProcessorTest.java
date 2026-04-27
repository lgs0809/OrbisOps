package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelChatProcessManager;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelOutboxBatchProcessorTest {

    @Test
    void processMustRecoverLeasesSelectBoundedWorkAndSummarizeOutcomes() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findDispatchableIds(4, 20)).thenReturn(List.of(1L, 2L));
        when(repository.tryAcquireLease(anyLong(), any(), any(), eq(4))).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(record(1L, "one")));
        when(repository.findById(2L)).thenReturn(Optional.of(record(2L, "two")));
        ChannelOutboundApplicationService outbound = mock(ChannelOutboundApplicationService.class);
        when(outbound.send(any()))
                .thenReturn(Map.of("delivered", true))
                .thenThrow(new IllegalStateException("channel unavailable"));
        OpsChannelOutboxDispatcher dispatcher = new OpsChannelOutboxDispatcher(
                repository,
                new ChannelChatProcessManager(outbound),
                new ChannelOutboundContentPolicy());
        OpsChannelOutboxBatchProcessor processor = new OpsChannelOutboxBatchProcessor(
                repository,
                dispatcher,
                new OpsChannelNotificationSettings(12_000, 4, 120));

        OpsChannelOutboxBatchProcessor.ProcessingSummary summary = processor.process(20);

        assertEquals(2, summary.processed());
        assertEquals(1, summary.success());
        assertEquals(1, summary.failed());
        assertEquals(2, summary.messages().size());
        verify(repository).recoverExpiredLeases(4);
        verify(repository).findDispatchableIds(4, 20);
        verify(repository).markSucceeded(eq(1L), any(), any());
        verify(repository).markFailed(eq(2L), any(), eq(1), any(), eq(false), any());
    }

    @Test
    void unavailableStoreMustBlockBatchProcessing() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(false);
        OpsChannelOutboxDispatcher dispatcher = new OpsChannelOutboxDispatcher(
                repository,
                new ChannelChatProcessManager(mock(ChannelOutboundApplicationService.class)),
                new ChannelOutboundContentPolicy());
        OpsChannelOutboxBatchProcessor processor = new OpsChannelOutboxBatchProcessor(
                repository,
                dispatcher,
                OpsChannelNotificationSettings.defaults());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> processor.process(10));

        assertEquals("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE", error.getMessage());
    }

    private ChannelOutboxRecord record(long id, String analysisId) {
        return new ChannelOutboxRecord(
                id, "dedup-" + id, "project", "channel", "target", analysisId, "PENDING",
                "message", "{}", "hash", 0, "", "",
                null, null, null, null, null, null);
    }
}
