package cn.lgs.orbisops.application.channel;

/** Result returned by one immediate durable outbox delivery attempt. */
public record ChannelDispatchOutcome(
        boolean success,
        String message,
        boolean appendExecutionNote) {
}
