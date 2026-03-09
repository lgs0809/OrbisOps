package cn.lgs.orbisops.trigger.ops;

/** Typed settings for asynchronous analysis run admission and fallback storage. */
public record OpsAnalysisRunSettings(
        int maxMemoryRecords,
        boolean allowInMemoryFallback,
        boolean rejectWhenQueueFull) {

    public OpsAnalysisRunSettings {
        maxMemoryRecords = Math.max(1, Math.min(maxMemoryRecords, 10_000));
    }

    public static OpsAnalysisRunSettings defaults() {
        return new OpsAnalysisRunSettings(200, false, true);
    }
}
