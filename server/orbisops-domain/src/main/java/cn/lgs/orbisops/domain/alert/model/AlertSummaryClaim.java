package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AlertSummaryClaim(
        String aggregateKey,
        String dispatchKey,
        String projectId,
        long ruleId,
        String fingerprint,
        String severity,
        int priority,
        long occurrenceCount,
        int summarizedOccurrences,
        Map<String, Object> payload,
        List<String> affectedResources,
        long version,
        String claimToken) {

    public AlertSummaryClaim {
        aggregateKey = required(aggregateKey, "ALERT_AGGREGATE_KEY_REQUIRED");
        dispatchKey = required(dispatchKey, "ALERT_DISPATCH_KEY_REQUIRED");
        projectId = required(projectId, "ALERT_AGGREGATE_PROJECT_ID_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_AGGREGATE_FINGERPRINT_REQUIRED");
        severity = required(severity, "ALERT_AGGREGATE_SEVERITY_REQUIRED");
        priority = Math.max(0, priority);
        occurrenceCount = Math.max(0L, occurrenceCount);
        summarizedOccurrences = Math.max(0, summarizedOccurrences);
        payload = copyMap(payload);
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
        version = Math.max(0L, version);
        claimToken = required(claimToken, "ALERT_SUMMARY_CLAIM_TOKEN_REQUIRED");
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
