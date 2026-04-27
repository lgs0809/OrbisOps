package cn.lgs.orbisops.trigger.ops.channel;

/** Typed Channel notification and durable outbox settings. */
public record OpsChannelNotificationSettings(
        int maxMessageChars,
        int maxAttempts,
        int leaseSeconds) {

    public static final int DEFAULT_MAX_MESSAGE_CHARS = 12_000;
    public static final int DEFAULT_MAX_ATTEMPTS = 8;
    public static final int DEFAULT_LEASE_SECONDS = 120;

    public OpsChannelNotificationSettings {
        maxMessageChars = Math.max(500, maxMessageChars);
        maxAttempts = Math.max(1, Math.min(maxAttempts, 20));
        leaseSeconds = Math.max(15, leaseSeconds);
    }

    public static OpsChannelNotificationSettings defaults() {
        return new OpsChannelNotificationSettings(
                DEFAULT_MAX_MESSAGE_CHARS,
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_LEASE_SECONDS);
    }
}
