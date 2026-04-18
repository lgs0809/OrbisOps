package cn.lgs.orbisops.trigger.ops.change;

/** Typed scheduling and reconciliation limits for expired landing operations. */
public record OpsLandingRecoverySettings(
        boolean enabled,
        int batchSize,
        int reconciliationTimeoutSeconds) {

    public OpsLandingRecoverySettings {
        batchSize = batchSize < 1 || batchSize > 1_000 ? 20 : batchSize;
        reconciliationTimeoutSeconds = reconciliationTimeoutSeconds < 5
                || reconciliationTimeoutSeconds > 3_600
                ? 120
                : reconciliationTimeoutSeconds;
    }

    public static OpsLandingRecoverySettings defaults() {
        return new OpsLandingRecoverySettings(true, 20, 120);
    }
}
