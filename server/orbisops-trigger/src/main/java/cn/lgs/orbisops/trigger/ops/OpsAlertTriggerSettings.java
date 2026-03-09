package cn.lgs.orbisops.trigger.ops;

/** Typed runtime limits for alert webhook, aggregation, and outbox execution. */
public record OpsAlertTriggerSettings(
        long signatureMaxSkewSeconds,
        int outboxMaxAttempts,
        int outboxLockTimeoutSeconds,
        int aggregationDebounceSeconds,
        int aggregationMaxWaitSeconds,
        int projectMaxQueued,
        int projectMaxRunning) {

    public OpsAlertTriggerSettings {
        signatureMaxSkewSeconds = bounded(signatureMaxSkewSeconds, 30L, 86_400L, 300L);
        outboxMaxAttempts = bounded(outboxMaxAttempts, 1, 100, 8);
        outboxLockTimeoutSeconds = bounded(outboxLockTimeoutSeconds, 30, 3_600, 120);
        aggregationDebounceSeconds = bounded(aggregationDebounceSeconds, 1, 86_400, 120);
        aggregationMaxWaitSeconds = bounded(
                aggregationMaxWaitSeconds,
                aggregationDebounceSeconds,
                604_800,
                Math.max(aggregationDebounceSeconds, 900));
        projectMaxQueued = bounded(projectMaxQueued, 1, 10_000, 100);
        projectMaxRunning = bounded(projectMaxRunning, 1, 100, 4);
    }

    public static OpsAlertTriggerSettings defaults() {
        return new OpsAlertTriggerSettings(300, 8, 120, 120, 900, 100, 4);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static long bounded(long value, long min, long max, long fallback) {
        return value < min || value > max ? fallback : value;
    }
}
