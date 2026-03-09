package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertAggregationSignal(
        String projectId,
        long ruleId,
        String fingerprint,
        AlertAggregateState state,
        String severity,
        int severityRank,
        String affectedResource,
        Map<String, Object> payload,
        int debounceSeconds,
        int maxWaitSeconds) {

    public AlertAggregationSignal {
        projectId = required(projectId, "ALERT_AGGREGATE_PROJECT_ID_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_AGGREGATE_FINGERPRINT_REQUIRED");
        if (state == null) throw new IllegalArgumentException("ALERT_AGGREGATE_STATE_REQUIRED");
        severity = required(severity, "ALERT_AGGREGATE_SEVERITY_REQUIRED");
        severityRank = Math.max(0, severityRank);
        affectedResource = text(affectedResource);
        payload = copyMap(payload);
        debounceSeconds = Math.max(1, debounceSeconds);
        maxWaitSeconds = Math.max(debounceSeconds, maxWaitSeconds);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
