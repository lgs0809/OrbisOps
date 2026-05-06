package cn.lgs.orbisops.trigger.ops;

/** Typed trigger and worker policy for Skill Evolution jobs. */
public record OpsSkillEvolutionSettings(
        boolean triggerEnabled,
        boolean workerEnabled,
        int batchSize,
        int maxAttempts,
        long fixedDelayMillis) {

    public OpsSkillEvolutionSettings {
        batchSize = bounded(batchSize, 1, 100, 5);
        maxAttempts = bounded(maxAttempts, 1, 20, 3);
        fixedDelayMillis = fixedDelayMillis < 1_000L || fixedDelayMillis > 3_600_000L
                ? 60_000L
                : fixedDelayMillis;
    }

    public static OpsSkillEvolutionSettings defaults() {
        return new OpsSkillEvolutionSettings(true, false, 5, 3, 60_000L);
    }

    static OpsSkillEvolutionSettings legacyConstructorDefaults() {
        return new OpsSkillEvolutionSettings(false, false, 5, 3, 60_000L);
    }

    private static int bounded(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
