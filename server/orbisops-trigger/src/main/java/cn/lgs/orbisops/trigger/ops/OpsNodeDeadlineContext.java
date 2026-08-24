package cn.lgs.orbisops.trigger.ops;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Propagates the current analysis-node wall-clock deadline to nested model calls.
 */
public final class OpsNodeDeadlineContext {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private OpsNodeDeadlineContext() {
    }

    public static <T> T withTimeout(Integer timeoutSeconds, Supplier<T> supplier) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return supplier.get();
        }
        long requestedDeadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(Math.max(1, timeoutSeconds));
        return withDeadline(requestedDeadline, supplier);
    }

    public static Long captureDeadline() { return DEADLINE_NANOS.get(); }

    public static <T> T withDeadline(Long requestedDeadline, Supplier<T> supplier) {
        if (requestedDeadline == null) return supplier.get();
        Long previous = DEADLINE_NANOS.get();
        long effectiveDeadline = previous == null
                ? requestedDeadline : Math.min(previous, requestedDeadline);
        DEADLINE_NANOS.set(effectiveDeadline);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        }
    }

    public static long remainingMillis(long fallbackMillis) {
        long fallback = Math.max(1L, fallbackMillis);
        Long deadline = DEADLINE_NANOS.get();
        if (deadline == null) {
            return fallback;
        }
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        return Math.max(0L, Math.min(fallback, remaining));
    }

    public static void assertNotExpired() {
        Long deadline = DEADLINE_NANOS.get();
        if (deadline != null && System.nanoTime() >= deadline) {
            throw new IllegalStateException("节点执行超过配置的总时限");
        }
    }
}
