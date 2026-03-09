package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelReplyOutboxPort;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelReplyOutboxAdapter implements ChannelReplyOutboxPort {

    private final OpsChannelNotificationService notifications;

    public OpsChannelReplyOutboxAdapter(OpsChannelNotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public ReplyDelivery enqueue(String projectId,
                                 String channelId,
                                 String target,
                                 String content,
                                 String runId,
                                 String sessionId,
                                 String replyToMessageId) {
        OpsChannelNotificationService.ReplyDelivery result = notifications.enqueueChatReply(
                projectId, channelId, target, content, runId, sessionId, replyToMessageId);
        return new ReplyDelivery(result.delivered(), result.terminalFailure());
    }
}
