package cn.lgs.orbisops.domain.alert.model;

import java.util.List;

public record AlertAggregationDecision(
        String aggregateKey,
        String dispatchKey,
        AlertAggregateEventType eventType,
        boolean dispatchNow,
        int priority,
        long occurrenceCount,
        int pendingSummaryCount,
        long version,
        List<String> affectedResources) {

    public AlertAggregationDecision {
        aggregateKey = required(aggregateKey, "ALERT_AGGREGATE_KEY_REQUIRED");
        dispatchKey = required(dispatchKey, "ALERT_DISPATCH_KEY_REQUIRED");
        if (eventType == null) throw new IllegalArgumentException("ALERT_AGGREGATION_EVENT_TYPE_REQUIRED");
        priority = Math.max(0, priority);
        occurrenceCount = Math.max(0L, occurrenceCount);
        pendingSummaryCount = Math.max(0, pendingSummaryCount);
        version = Math.max(0L, version);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
