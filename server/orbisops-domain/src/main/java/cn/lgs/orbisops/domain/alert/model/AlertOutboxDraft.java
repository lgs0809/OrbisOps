package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertOutboxDraft(
        String dispatchKey,
        long ruleId,
        String projectId,
        String fingerprint,
        String aggregateKey,
        AlertAggregateEventType eventType,
        int priority,
        AlertRunRequest request,
        Map<String, Object> payload) {

    public AlertOutboxDraft {
        dispatchKey = required(dispatchKey, "ALERT_OUTBOX_DISPATCH_KEY_REQUIRED");
        projectId = required(projectId, "ALERT_OUTBOX_PROJECT_ID_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_OUTBOX_FINGERPRINT_REQUIRED");
        aggregateKey = required(aggregateKey, "ALERT_OUTBOX_AGGREGATE_KEY_REQUIRED");
        if (eventType == null) throw new IllegalArgumentException("ALERT_OUTBOX_EVENT_TYPE_REQUIRED");
        priority = Math.max(0, priority);
        if (request == null) throw new IllegalArgumentException("ALERT_OUTBOX_REQUEST_REQUIRED");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
