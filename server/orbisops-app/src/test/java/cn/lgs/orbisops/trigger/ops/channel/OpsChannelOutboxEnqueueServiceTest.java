package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelOutboxEnqueueServiceTest {

    @Test
    void analysisCommandMustValidateChannelBeforePersistingProjectedRecord() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        ChannelQueryService channels = mock(ChannelQueryService.class);
        when(repository.available()).thenReturn(true);
        when(repository.enqueue(any())).thenReturn(7L);
        OpsChannelOutboxEnqueueService service = service(repository, channels, mock(OpsConfigAuditService.class));

        long id = service.enqueueAnalysis(new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                "project-1", "channel-1", "room-1", "run-1", "analysis-1", "time", "report"));

        assertEquals(7L, id);
        verify(channels).get("project-1", "channel-1");
        ArgumentCaptor<ChannelOutboxRecord> record = ArgumentCaptor.forClass(ChannelOutboxRecord.class);
        verify(repository).enqueue(record.capture());
        assertEquals("analysis-1", record.getValue().analysisId());
    }

    @Test
    void replyCommandMustAuditAfterPersisting() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        ChannelQueryService channels = mock(ChannelQueryService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        when(repository.available()).thenReturn(true);
        when(repository.enqueue(any())).thenReturn(9L);
        OpsChannelOutboxEnqueueService service = service(repository, channels, audit);

        long id = service.enqueueReply(new OpsChannelOutboxRecordFactory.ChatReplyDraft(
                "project-1", "channel-1", "room-1", "reply",
                "run-1", "session-1", "message-1"));

        assertEquals(9L, id);
        verify(audit).recordRuntimeEvent(
                eq("project-1"), eq(""), eq("channel-runtime"), eq("channel"),
                eq("CHANNEL_REPLY_ENQUEUED"), eq("9"), eq("LOW"), eq("QUEUED"),
                eq(Map.of(
                        "channelId", "channel-1",
                        "outboxId", 9L,
                        "runId", "run-1",
                        "sessionId", "session-1",
                        "sourceMessageId", "message-1")));
    }

    @Test
    void replyAuditFailureMustCancelPersistedRecordBeforeDelivery() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.enqueue(any())).thenReturn(12L);
        when(repository.cancel("project-1", 12L)).thenReturn(true);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        doThrow(new IllegalStateException("audit down")).when(audit)
                .recordRuntimeEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        OpsChannelOutboxEnqueueService service = service(repository, mock(ChannelQueryService.class), audit);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.enqueueReply(new OpsChannelOutboxRecordFactory.ChatReplyDraft(
                        "project-1", "channel-1", "room-1", "reply",
                        "run", "session", "message")));

        assertTrue(error.getMessage().contains("CHANNEL_REPLY_AUDIT_FAILED_BEFORE_SEND"));
        verify(repository).cancel("project-1", 12L);
        verify(repository, never()).tryAcquireLease(anyLong(), any(), any(), anyInt());
    }

    @Test
    void unavailableStoreMustBlockEnqueue() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(false);
        OpsChannelOutboxEnqueueService service = service(
                repository, mock(ChannelQueryService.class), mock(OpsConfigAuditService.class));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.enqueueAnalysis(new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                        "project", "channel", "target", "", "analysis", "time", "report")));

        assertEquals("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE", error.getMessage());
    }

    private OpsChannelOutboxEnqueueService service(
            IChannelOutboxRepository repository,
            ChannelQueryService channels,
            OpsConfigAuditService audit) {
        return new OpsChannelOutboxEnqueueService(
                repository,
                channels,
                new OpsChannelOutboxRecordFactory(
                        new ChannelOutboundContentPolicy(),
                        OpsChannelNotificationSettings.defaults()),
                audit);
    }
}
