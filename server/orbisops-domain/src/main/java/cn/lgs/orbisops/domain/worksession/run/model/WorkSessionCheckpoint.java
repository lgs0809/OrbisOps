package cn.lgs.orbisops.domain.worksession.run.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkSessionCheckpoint(
        String checkpointType,
        Map<String, Object> payload,
        String payloadHash,
        Instant createdAt) {

    public WorkSessionCheckpoint {
        checkpointType = required(checkpointType, "checkpointType 不能为空");
        payload = immutable(payload);
        payloadHash = required(payloadHash, "WORK_SESSION_CHECKPOINT_HASH_REQUIRED");
        if (createdAt == null) throw new IllegalArgumentException("WORK_SESSION_CHECKPOINT_TIME_REQUIRED");
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
