package cn.lgs.orbisops.trigger.ops;

/** Resolved and bounded specification for one trace-aware executor pool. */
public record OpsExecutorPoolSettings(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        long keepAliveSeconds,
        String rejectionPolicy,
        String threadNamePrefix) {

    public OpsExecutorPoolSettings {
        corePoolSize = Math.max(1, corePoolSize);
        maxPoolSize = Math.max(corePoolSize, maxPoolSize);
        queueCapacity = Math.max(1, queueCapacity);
        keepAliveSeconds = Math.max(1L, keepAliveSeconds);
        rejectionPolicy = rejectionPolicy == null ? "" : rejectionPolicy.trim();
        threadNamePrefix = threadNamePrefix == null ? "ops-worker-" : threadNamePrefix;
    }

    public static OpsExecutorPoolSettings resolve(
            Integer corePoolSize,
            Integer maxPoolSize,
            Integer queueCapacity,
            Long keepAliveSeconds,
            String rejectionPolicy,
            String threadNamePrefix,
            int defaultCore,
            int defaultMax,
            int defaultQueueCapacity,
            String defaultRejectionPolicy) {
        return new OpsExecutorPoolSettings(
                value(corePoolSize, defaultCore),
                value(maxPoolSize, defaultMax),
                value(queueCapacity, defaultQueueCapacity),
                value(keepAliveSeconds, 30L),
                rejectionPolicy == null ? defaultRejectionPolicy : rejectionPolicy,
                threadNamePrefix);
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static long value(Long value, long fallback) {
        return value == null ? fallback : value;
    }
}
