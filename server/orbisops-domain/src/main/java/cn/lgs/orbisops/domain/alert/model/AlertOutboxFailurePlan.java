package cn.lgs.orbisops.domain.alert.model;

public record AlertOutboxFailurePlan(
        int nextRetryCount,
        int retryDelaySeconds,
        boolean deadLetter,
        String errorMessage) {

    public AlertOutboxFailurePlan {
        nextRetryCount = Math.max(1, nextRetryCount);
        retryDelaySeconds = Math.max(1, retryDelaySeconds);
        errorMessage = errorMessage == null || errorMessage.isBlank()
                ? "ALERT_OUTBOX_SUBMISSION_FAILED"
                : errorMessage.trim();
    }
}
