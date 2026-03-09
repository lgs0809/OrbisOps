package cn.lgs.orbisops.trigger.ops;

/** Typed retention policy for completed cancellation signals. */
public record OpsRunCancellationSettings(long retentionSeconds) {

    public OpsRunCancellationSettings {
        retentionSeconds = retentionSeconds < 60L || retentionSeconds > 86_400L
                ? 600L
                : retentionSeconds;
    }

    public static OpsRunCancellationSettings defaults() {
        return new OpsRunCancellationSettings(600L);
    }

    static OpsRunCancellationSettings legacyConstructorDefaults() {
        return new OpsRunCancellationSettings(60L);
    }

    long retentionMillis() {
        return retentionSeconds * 1_000L;
    }
}
