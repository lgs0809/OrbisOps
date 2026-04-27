package cn.lgs.orbisops.application.channel;

/** Durable Channel outbox boundary used by notification orchestration. */
public interface ChannelNotificationDeliveryPort {

    boolean available();

    long enqueueAnalysis(ChannelAnalysisNotificationCommand command);

    long enqueueReply(ChannelChatReplyCommand command);

    ChannelDispatchOutcome dispatch(long outboxId);

    String statusOf(long outboxId);

    String sanitizeFailure(String message);
}
