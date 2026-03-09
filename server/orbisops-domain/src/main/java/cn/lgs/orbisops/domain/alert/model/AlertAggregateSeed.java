package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AlertAggregateSeed(
        String aggregateKey,
        String projectId,
        long ruleId,
        String fingerprint,
        AlertAggregateState state,
        String severity,
        int severityRank,
        List<String> affectedResources,
        Map<String, Object> payload) {

    public AlertAggregateSeed {
        aggregateKey = required(aggregateKey, "ALERT_AGGREGATE_KEY_REQUIRED");
        projectId = required(projectId, "ALERT_AGGREGATE_PROJECT_ID_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_AGGREGATE_FINGERPRINT_REQUIRED");
        if (state == null) throw new IllegalArgumentException("ALERT_AGGREGATE_STATE_REQUIRED");
        severity = required(severity, "ALERT_AGGREGATE_SEVERITY_REQUIRED");
        severityRank = Math.max(0, severityRank);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
        payload = copyMap(payload);
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
