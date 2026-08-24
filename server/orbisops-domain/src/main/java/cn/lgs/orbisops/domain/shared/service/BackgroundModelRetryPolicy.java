package cn.lgs.orbisops.domain.shared.service;

/** A persisted queue decides when to call again; this never retries an HTTP call or a tool write. */
public final class BackgroundModelRetryPolicy {
    public static final long MAX_DELAY_MILLIS = 3_600_000L;
    private BackgroundModelRetryPolicy() { }

    /** Stable jitter keeps a restored job's identity; callers persist the resulting due time once. */
    public static long delayMillis(String identity, int attempts, long baseMillis, long retryAfterMillis) {
        long base = Math.max(1, Math.min(MAX_DELAY_MILLIS, baseMillis));
        long exponential = Math.min(MAX_DELAY_MILLIS, base * (1L << Math.min(20, Math.max(0, attempts - 1))));
        long jitter = Math.floorMod(java.util.Objects.hash(identity, attempts), Math.max(1L, exponential / 10 + 1));
        return Math.max(Math.min(MAX_DELAY_MILLIS, exponential + jitter), Math.max(0, retryAfterMillis));
    }
}
