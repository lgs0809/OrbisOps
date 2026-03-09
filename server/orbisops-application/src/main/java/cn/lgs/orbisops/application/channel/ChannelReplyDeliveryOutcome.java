package cn.lgs.orbisops.application.channel;

/** Durable outbox and immediate delivery result for one Channel chat reply. */
public record ChannelReplyDeliveryOutcome(
        long outboxId,
        boolean delivered,
        String status,
        String message) {

    public boolean terminalFailure() {
        return "DEAD_LETTER".equals(status) || "CANCELLED".equals(status);
    }
}
