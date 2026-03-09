package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed safety envelope for governed runtime tool execution. */
public record OpsToolExecutionSettings(
        boolean enabled,
        boolean allowReview,
        boolean allowProduction,
        int maxTimeoutSeconds,
        long maxOutputBytes,
        int maxConcurrency) {

    public OpsToolExecutionSettings {
        maxTimeoutSeconds = maxTimeoutSeconds < 1 || maxTimeoutSeconds > 3_600
                ? 60
                : maxTimeoutSeconds;
        maxOutputBytes = maxOutputBytes < 1_024L || maxOutputBytes > 64L * 1024L * 1024L
                ? 262_144L
                : maxOutputBytes;
        maxConcurrency = maxConcurrency < 1 || maxConcurrency > 100
                ? 1
                : maxConcurrency;
    }

    public static OpsToolExecutionSettings defaults() {
        return new OpsToolExecutionSettings(
                true,
                false,
                false,
                60,
                262_144L,
                1);
    }
}
