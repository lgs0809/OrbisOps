package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed scheduling policy for expired durable work-session recovery. */
public record OpsWorkSessionRecoverySettings(boolean enabled, int batchSize) {

    public OpsWorkSessionRecoverySettings {
        batchSize = batchSize < 1 || batchSize > 1_000 ? 100 : batchSize;
    }

    public static OpsWorkSessionRecoverySettings defaults() {
        return new OpsWorkSessionRecoverySettings(true, 100);
    }
}
