package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AlertAggregationPlan(
        String aggregateKey,
        AlertAggregationAction action,
        AlertAggregateEventType eventType,
        AlertAggregateState targetState,
        String severity,
        int severityRank,
        List<String> affectedResources,
        Map<String, Object> payload,
        int debounceSeconds,
        int maxWaitSeconds,
        long expectedVersion) {

    public AlertAggregationPlan {
        aggregateKey = required(aggregateKey, "ALERT_AGGREGATE_KEY_REQUIRED");
        if (action == null) throw new IllegalArgumentException("ALERT_AGGREGATION_ACTION_REQUIRED");
        if (eventType == null) throw new IllegalArgumentException("ALERT_AGGREGATION_EVENT_TYPE_REQUIRED");
        if (targetState == null) throw new IllegalArgumentException("ALERT_AGGREGATE_STATE_REQUIRED");
        severity = required(severity, "ALERT_AGGREGATE_SEVERITY_REQUIRED");
        severityRank = Math.max(0, severityRank);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
        payload = copyMap(payload);
        debounceSeconds = Math.max(1, debounceSeconds);
        maxWaitSeconds = Math.max(debounceSeconds, maxWaitSeconds);
        expectedVersion = Math.max(0L, expectedVersion);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
