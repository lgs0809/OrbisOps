package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed policy for hot-context compression and optional model summarization. */
public record OpsContextCompressionSettings(
        boolean enabled,
        int thresholdMessages,
        int keepRecent,
        boolean modelEnabled,
        int modelMaxInputChars) {

    public OpsContextCompressionSettings {
        thresholdMessages = bounded(thresholdMessages, 1, 10_000, 20);
        keepRecent = bounded(keepRecent, 0, 10_000, 8);
        modelMaxInputChars = bounded(modelMaxInputChars, 0, 200_000, 6_000);
    }

    public static OpsContextCompressionSettings defaults() {
        return new OpsContextCompressionSettings(true, 20, 8, true, 6_000);
    }

    static OpsContextCompressionSettings legacyConstructorDefaults() {
        return new OpsContextCompressionSettings(false, 20, 8, false, 6_000);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
