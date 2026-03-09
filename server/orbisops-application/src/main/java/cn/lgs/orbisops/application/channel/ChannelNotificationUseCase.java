package cn.lgs.orbisops.application.channel;

/** Application process manager for optional analysis notifications and durable chat replies. */
public final class ChannelNotificationUseCase {

    private final ChannelNotificationDeliveryPort deliveryPort;

    public ChannelNotificationUseCase(ChannelNotificationDeliveryPort deliveryPort) {
        if (deliveryPort == null) {
            throw new IllegalArgumentException("CHANNEL_NOTIFICATION_DELIVERY_PORT_REQUIRED");
        }
        this.deliveryPort = deliveryPort;
    }

    public ChannelNotificationOutcome notifyIfNeeded(ChannelAnalysisNotificationCommand command) {
        if (command == null || !command.requested()) {
            return new ChannelNotificationOutcome(
                    false,
                    false,
                    "未配置完成后 Channel 通知。",
                    false);
        }
        if (!deliveryPort.available()) {
            return new ChannelNotificationOutcome(
                    true,
                    false,
                    "Channel 通知存储不可用，未执行投递。",
                    false);
        }
        try {
            long outboxId = deliveryPort.enqueueAnalysis(command);
            ChannelDispatchOutcome dispatch = deliveryPort.dispatch(outboxId);
            return new ChannelNotificationOutcome(
                    true,
                    dispatch.success(),
                    dispatch.message(),
                    dispatch.appendExecutionNote() || !dispatch.success());
        } catch (Exception error) {
            String message = "Channel 通知失败：" + deliveryPort.sanitizeFailure(error.getMessage());
            return new ChannelNotificationOutcome(true, false, message, true);
        }
    }

    public ChannelReplyDeliveryOutcome enqueueChatReply(ChannelChatReplyCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("CHANNEL_CHAT_REPLY_COMMAND_REQUIRED");
        }
        long outboxId = deliveryPort.enqueueReply(command);
        ChannelDispatchOutcome dispatch = deliveryPort.dispatch(outboxId);
        return new ChannelReplyDeliveryOutcome(
                outboxId,
                dispatch.success(),
                deliveryPort.statusOf(outboxId),
                dispatch.message());
    }
}
