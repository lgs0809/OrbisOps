package cn.lgs.orbisops.application.channel;

/** Typed durable Channel reply request. */
public record ChannelChatReplyCommand(
        String projectId,
        String channelId,
        String target,
        String content,
        String runId,
        String sessionId,
        String replyToMessageId) {
}
