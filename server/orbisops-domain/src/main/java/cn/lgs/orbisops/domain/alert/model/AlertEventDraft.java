package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertEventDraft(
        Long ruleId,
        String ruleName,
        String projectId,
        String sourceType,
        String status,
        String dispatchKey,
        String fingerprint,
        String alertName,
        String severity,
        String serviceName,
        String receiver,
        String runId,
        String runStatus,
        String finalSummary,
        String errorMessage,
        Map<String, Object> labels,
        Map<String, Object> annotations,
        Map<String, Object> payload) {

    public AlertEventDraft {
        ruleName = text(ruleName);
        projectId = required(projectId, "ALERT_EVENT_PROJECT_ID_REQUIRED");
        sourceType = required(sourceType, "ALERT_EVENT_SOURCE_REQUIRED");
        status = required(status, "ALERT_EVENT_STATUS_REQUIRED");
        dispatchKey = required(dispatchKey, "ALERT_EVENT_DISPATCH_KEY_REQUIRED");
        fingerprint = required(fingerprint, "ALERT_EVENT_FINGERPRINT_REQUIRED");
        alertName = text(alertName);
        severity = text(severity);
        serviceName = text(serviceName);
        receiver = text(receiver);
        runId = text(runId);
        runStatus = text(runStatus);
        finalSummary = text(finalSummary);
        errorMessage = text(errorMessage);
        labels = copy(labels);
        annotations = copy(annotations);
        payload = copy(payload);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
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
