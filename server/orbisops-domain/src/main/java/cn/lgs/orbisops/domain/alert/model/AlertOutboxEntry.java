package cn.lgs.orbisops.domain.alert.model;

public record AlertOutboxEntry(
        long id,
        String dispatchKey,
        long ruleId,
        String projectId,
        String fingerprint,
        String aggregateKey,
        AlertAggregateEventType eventType,
        AlertOutboxStatus status,
        AlertRunRequest request,
        int retryCount,
        int priority) {

    public AlertOutboxEntry {
        if (id <= 0) throw new IllegalArgumentException("ALERT_OUTBOX_ID_REQUIRED");
        dispatchKey = required(dispatchKey, "ALERT_OUTBOX_DISPATCH_KEY_REQUIRED");
        projectId = required(projectId, "ALERT_OUTBOX_PROJECT_ID_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_OUTBOX_FINGERPRINT_REQUIRED");
        aggregateKey = required(aggregateKey, "ALERT_OUTBOX_AGGREGATE_KEY_REQUIRED");
        if (eventType == null) throw new IllegalArgumentException("ALERT_OUTBOX_EVENT_TYPE_REQUIRED");
        if (status == null) throw new IllegalArgumentException("ALERT_OUTBOX_STATUS_REQUIRED");
        if (request == null) throw new IllegalArgumentException("ALERT_OUTBOX_REQUEST_REQUIRED");
        retryCount = Math.max(0, retryCount);
        priority = Math.max(0, priority);
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
