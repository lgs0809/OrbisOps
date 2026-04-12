package cn.lgs.orbisops.trigger.ops;

/** Typed and bounded settings for investigation execution orchestration. */
public record OpsInvestigationExecutorSettings(
        boolean mainReflectionLlmEnabled,
        boolean parallelExecutionEnabled,
        int maxTaskExecutions,
        int maxAdjustmentsLimit,
        int defaultMaxEvidenceItems) {

    public OpsInvestigationExecutorSettings {
        maxTaskExecutions = Math.max(1, Math.min(maxTaskExecutions, 100));
        maxAdjustmentsLimit = Math.max(0, Math.min(maxAdjustmentsLimit, 20));
        defaultMaxEvidenceItems = Math.max(1, Math.min(defaultMaxEvidenceItems, 100));
    }

    public static OpsInvestigationExecutorSettings defaults() {
        return new OpsInvestigationExecutorSettings(true, true, 8, 3, 12);
    }
}
