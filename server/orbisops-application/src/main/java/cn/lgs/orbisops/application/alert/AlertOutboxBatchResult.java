package cn.lgs.orbisops.application.alert;

public record AlertOutboxBatchResult(
        int scanned,
        int submitted,
        int failed,
        int recovered,
        int deadLettered) {

    public AlertOutboxBatchResult {
        scanned = Math.max(0, scanned);
        submitted = Math.max(0, submitted);
        failed = Math.max(0, failed);
        recovered = Math.max(0, recovered);
        deadLettered = Math.max(0, deadLettered);
    }
}
