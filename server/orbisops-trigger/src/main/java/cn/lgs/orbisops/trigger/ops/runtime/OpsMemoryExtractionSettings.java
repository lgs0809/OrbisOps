package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed policy for one-message memory candidate extraction. */
public record OpsMemoryExtractionSettings(
        boolean enabled,
        int maxItemsPerMessage,
        boolean modelEnabled,
        int modelMaxInputChars) {

    public OpsMemoryExtractionSettings {
        maxItemsPerMessage = bounded(maxItemsPerMessage, 0, 100, 6);
        modelMaxInputChars = bounded(modelMaxInputChars, 0, 200_000, 4_000);
    }

    public static OpsMemoryExtractionSettings defaults() {
        return new OpsMemoryExtractionSettings(true, 6, true, 4_000);
    }

    static OpsMemoryExtractionSettings legacyConstructorDefaults() {
        return new OpsMemoryExtractionSettings(false, 6, false, 4_000);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
