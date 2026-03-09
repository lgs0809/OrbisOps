package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed runtime policy for hot, cold, semantic, and capture memory coordination. */
public record OpsMemoryFacadeSettings(
        boolean enabled,
        int hotMaxMessages,
        int hotBufferMessages,
        int itemMatchLimit,
        int semanticTopK,
        int contextMaxChars,
        long assembleTimeoutMillis,
        boolean extractionAsyncEnabled,
        boolean recencyAwareEnabled,
        double recencyHalfLifeTurns) {

    public OpsMemoryFacadeSettings {
        hotMaxMessages = bounded(hotMaxMessages, 1, 10_000, 12);
        hotBufferMessages = bounded(hotBufferMessages, 2, 20_000, 24);
        itemMatchLimit = bounded(itemMatchLimit, 0, 1_000, 8);
        semanticTopK = bounded(semanticTopK, 0, 1_000, 8);
        contextMaxChars = bounded(contextMaxChars, 0, 200_000, 8_000);
        assembleTimeoutMillis = bounded(assembleTimeoutMillis, 1L, 120_000L, 1_200L);
        recencyHalfLifeTurns = Double.isFinite(recencyHalfLifeTurns) && recencyHalfLifeTurns > 0D
                ? recencyHalfLifeTurns
                : 6D;
    }

    public static OpsMemoryFacadeSettings defaults() {
        return new OpsMemoryFacadeSettings(
                true, 12, 24, 8, 8, 8_000, 1_200L, true, true, 6D);
    }

    static OpsMemoryFacadeSettings legacyConstructorDefaults() {
        return new OpsMemoryFacadeSettings(
                false, 12, 24, 8, 8, 8_000, 1_200L, false, true, 6D);
    }

    int captureBufferSize() {
        return Math.max(Math.max(2, hotMaxMessages), hotBufferMessages);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private static long bounded(long value, long min, long max, long fallback) {
        return value < min || value > max ? fallback : value;
    }
}
