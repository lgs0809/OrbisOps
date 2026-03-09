package cn.lgs.orbisops.domain.alert.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AlertEventSnapshot(
        Long id,
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
        String completedAt,
        String errorMessage,
        Map<String, Object> labels,
        Map<String, Object> annotations,
        Map<String, Object> payload,
        String createTime) {

    public AlertEventSnapshot {
        labels = copy(labels);
        annotations = copy(annotations);
        payload = copy(payload);
        completedAt = text(completedAt);
        createTime = text(createTime);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
