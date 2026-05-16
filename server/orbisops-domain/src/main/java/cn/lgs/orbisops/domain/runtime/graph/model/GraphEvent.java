package cn.lgs.orbisops.domain.runtime.graph.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GraphEvent(
        String runId,
        String analysisId,
        long sequence,
        String eventType,
        String nodeId,
        String nodeType,
        String agent,
        String source,
        String status,
        String summary,
        String startedAt,
        String finishedAt,
        Long durationMs,
        Map<String, Object> payload) {

    public GraphEvent {
        runId = text(runId);
        analysisId = text(analysisId);
        if (runId.isBlank() && analysisId.isBlank()) {
            throw new IllegalArgumentException("GRAPH_EVENT_SCOPE_REQUIRED");
        }
        if (sequence <= 0L) throw new IllegalArgumentException("GRAPH_EVENT_SEQUENCE_INVALID");
        eventType = required(eventType, "GRAPH_EVENT_TYPE_REQUIRED");
        nodeId = text(nodeId);
        nodeType = text(nodeType);
        agent = text(agent);
        source = text(source);
        status = text(status);
        summary = text(summary);
        startedAt = text(startedAt);
        finishedAt = text(finishedAt);
        payload = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public String sequenceKey() {
        return runId.isBlank() ? analysisId : runId;
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
