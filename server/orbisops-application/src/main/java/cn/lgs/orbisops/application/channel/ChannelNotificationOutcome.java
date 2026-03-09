package cn.lgs.orbisops.application.channel;

/** Business outcome for an optional analysis-completion Channel notification. */
public record ChannelNotificationOutcome(
        boolean attempted,
        boolean success,
        String message,
        boolean appendExecutionNote) {
}
