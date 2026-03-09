package cn.lgs.orbisops.trigger.job;

/** Typed policy for scheduled alert outbox draining. */
public record OpsAlertOutboxJobSettings(boolean enabled, int batchSize) {

    public OpsAlertOutboxJobSettings {
        batchSize = batchSize < 1 || batchSize > 1_000 ? 20 : batchSize;
    }

    public static OpsAlertOutboxJobSettings defaults() {
        return new OpsAlertOutboxJobSettings(true, 20);
    }

    static OpsAlertOutboxJobSettings legacyConstructorDefaults() {
        return new OpsAlertOutboxJobSettings(false, 20);
    }
}
