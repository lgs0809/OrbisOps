package cn.lgs.orbisops.application.channel;

public interface ChannelReplyOutboxPort {

    ReplyDelivery enqueue(String projectId,
                          String channelId,
                          String target,
                          String content,
                          String runId,
                          String sessionId,
                          String replyToMessageId);

    record ReplyDelivery(boolean delivered, boolean terminalFailure) {
    }
}
