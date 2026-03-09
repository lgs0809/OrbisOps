package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Validates Channel targets and persists durable analysis/reply outbox commands. */
public final class OpsChannelOutboxEnqueueService {

    private final IChannelOutboxRepository repository;
    private final ChannelQueryService channelQueryService;
    private final OpsChannelOutboxRecordFactory recordFactory;
    private final OpsConfigAuditService auditService;

    public OpsChannelOutboxEnqueueService(
            IChannelOutboxRepository repository,
            ChannelQueryService channelQueryService,
            OpsChannelOutboxRecordFactory recordFactory,
            OpsConfigAuditService auditService) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_REPOSITORY_REQUIRED");
        if (channelQueryService == null) throw new IllegalArgumentException("CHANNEL_QUERY_SERVICE_REQUIRED");
        if (recordFactory == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_RECORD_FACTORY_REQUIRED");
        if (auditService == null) throw new IllegalArgumentException("CONFIG_AUDIT_SERVICE_REQUIRED");
        this.repository = repository;
        this.channelQueryService = channelQueryService;
        this.recordFactory = recordFactory;
        this.auditService = auditService;
    }

    public long enqueueAnalysis(OpsChannelOutboxRecordFactory.AnalysisNotificationDraft draft) {
        requireAvailable();
        ChannelOutboxRecord record = recordFactory.analysisNotification(draft);
        channelQueryService.get(record.projectId(), record.channelId());
        return repository.enqueue(record);
    }

    public long enqueueReply(OpsChannelOutboxRecordFactory.ChatReplyDraft draft) {
        requireAvailable();
        ChannelOutboxRecord record = recordFactory.chatReply(draft);
        channelQueryService.get(record.projectId(), record.channelId());
        long id = repository.enqueue(record);
        try {
            auditService.recordRuntimeEvent(
                    record.projectId(),
                    "",
                    "channel-runtime",
                    "channel",
                    "CHANNEL_REPLY_ENQUEUED",
                    String.valueOf(id),
                    "LOW",
                    "QUEUED",
                    Map.of(
                            "channelId", record.channelId(),
                            "outboxId", id,
                            "runId", text(draft.runId()),
                            "sessionId", text(draft.sessionId()),
                            "sourceMessageId", record.analysisId()));
        } catch (RuntimeException auditFailure) {
            if (!repository.cancel(record.projectId(), id)) {
                throw new IllegalStateException(
                        "CHANNEL_REPLY_AUDIT_FAILED_AND_OUTBOX_CANCEL_FAILED",
                        auditFailure);
            }
            throw new IllegalStateException(
                    "CHANNEL_REPLY_AUDIT_FAILED_BEFORE_SEND",
                    auditFailure);
        }
        return id;
    }

    public boolean available() {
        return repository.available();
    }

    private void requireAvailable() {
        if (!repository.available()) {
            throw new IllegalStateException("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
