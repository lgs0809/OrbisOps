package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelAnalysisNotificationCommand;
import cn.lgs.orbisops.application.channel.ChannelChatReplyCommand;
import cn.lgs.orbisops.application.channel.ChannelDispatchOutcome;
import cn.lgs.orbisops.application.channel.ChannelNotificationDeliveryPort;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

/** Durable outbox adapter for notification enqueue, immediate delivery and status. */
public final class OpsChannelNotificationDeliveryAdapter implements ChannelNotificationDeliveryPort {

    private final OpsChannelOutboxEnqueueService enqueueService;
    private final OpsChannelOutboxDispatcher dispatcher;
    private final OpsChannelOutboxQueryService queryService;
    private final ChannelOutboundContentPolicy contentPolicy;
    private final OpsChannelNotificationSettings settings;

    public OpsChannelNotificationDeliveryAdapter(
            OpsChannelOutboxEnqueueService enqueueService,
            OpsChannelOutboxDispatcher dispatcher,
            OpsChannelOutboxQueryService queryService,
            ChannelOutboundContentPolicy contentPolicy,
            OpsChannelNotificationSettings settings) {
        if (enqueueService == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_ENQUEUE_SERVICE_REQUIRED");
        if (dispatcher == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_DISPATCHER_REQUIRED");
        if (queryService == null) throw new IllegalArgumentException("CHANNEL_OUTBOX_QUERY_SERVICE_REQUIRED");
        if (contentPolicy == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_CONTENT_POLICY_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("CHANNEL_NOTIFICATION_SETTINGS_REQUIRED");
        this.enqueueService = enqueueService;
        this.dispatcher = dispatcher;
        this.queryService = queryService;
        this.contentPolicy = contentPolicy;
        this.settings = settings;
    }

    @Override
    public boolean available() {
        return enqueueService.available();
    }

    @Override
    public long enqueueAnalysis(ChannelAnalysisNotificationCommand command) {
        return enqueueService.enqueueAnalysis(new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                command.projectId(),
                command.channelId(),
                command.target(),
                command.runId(),
                command.analysisId(),
                command.generatedAt(),
                command.markdownReport()));
    }

    @Override
    public long enqueueReply(ChannelChatReplyCommand command) {
        return enqueueService.enqueueReply(new OpsChannelOutboxRecordFactory.ChatReplyDraft(
                command.projectId(),
                command.channelId(),
                command.target(),
                command.content(),
                command.runId(),
                command.sessionId(),
                command.replyToMessageId()));
    }

    @Override
    public ChannelDispatchOutcome dispatch(long outboxId) {
        OpsChannelOutboxDispatcher.Result result = dispatcher.dispatch(
                outboxId,
                new OpsChannelOutboxDispatcher.Settings(
                        settings.maxAttempts(),
                        settings.leaseSeconds()));
        return new ChannelDispatchOutcome(
                result.success(),
                result.message(),
                result.appendExecutionNote());
    }

    @Override
    public String statusOf(long outboxId) {
        return queryService.statusOf(outboxId);
    }

    @Override
    public String sanitizeFailure(String message) {
        return contentPolicy.sanitize(message);
    }
}
