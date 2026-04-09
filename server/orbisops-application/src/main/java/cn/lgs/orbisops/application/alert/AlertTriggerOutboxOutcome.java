package cn.lgs.orbisops.application.alert;

/** Stable orchestration outcome for one alert-trigger outbox batch. */
public record AlertTriggerOutboxOutcome(
        int scanned,
        int submitted,
        int failed,
        int recovered,
        int deadLettered,
        int summariesQueued
) {

    public AlertTriggerOutboxOutcome {
        scanned = Math.max(0, scanned);
        submitted = Math.max(0, submitted);
        failed = Math.max(0, failed);
        recovered = Math.max(0, recovered);
        deadLettered = Math.max(0, deadLettered);
        summariesQueued = Math.max(0, summariesQueued);
    }

    public static AlertTriggerOutboxOutcome from(
            AlertOutboxBatchResult batch,
            int summariesQueued) {
        if (batch == null) {
            throw new IllegalArgumentException("ALERT_OUTBOX_BATCH_RESULT_REQUIRED");
        }
        return new AlertTriggerOutboxOutcome(
                batch.scanned(),
                batch.submitted(),
                batch.failed(),
                batch.recovered(),
                batch.deadLettered(),
                summariesQueued);
    }

    public boolean hasActivity() {
        return submitted > 0 || failed > 0 || recovered > 0 || deadLettered > 0
                || summariesQueued > 0;
    }
}
