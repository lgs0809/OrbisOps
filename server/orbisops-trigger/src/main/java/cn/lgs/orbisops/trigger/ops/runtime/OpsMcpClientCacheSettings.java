package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed TTL and cleanup cadence for reusable MCP clients. */
public record OpsMcpClientCacheSettings(
        long ttlSeconds,
        long cleanupIntervalMillis) {

    public OpsMcpClientCacheSettings {
        ttlSeconds = ttlSeconds < 1L || ttlSeconds > 86_400L
                ? 600L
                : ttlSeconds;
        cleanupIntervalMillis = cleanupIntervalMillis < 1_000L
                || cleanupIntervalMillis > 3_600_000L
                ? 60_000L
                : cleanupIntervalMillis;
    }

    public static OpsMcpClientCacheSettings defaults() {
        return new OpsMcpClientCacheSettings(600L, 60_000L);
    }

    static OpsMcpClientCacheSettings legacyConstructorDefaults() {
        return new OpsMcpClientCacheSettings(1L, 60_000L);
    }

    long ttlMillis() {
        return ttlSeconds * 1_000L;
    }
}
