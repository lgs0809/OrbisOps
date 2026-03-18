package cn.lgs.orbisops.domain.evidence.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EvidenceDraft(
        String projectId,
        String runId,
        String sourceType,
        String sourceId,
        String toolResultId,
        String outputHash,
        String fullOutputRef,
        String summary,
        boolean verified,
        Map<String, Object> metadata,
        String actor) {

    public EvidenceDraft {
        projectId = required(projectId, "Evidence 缺失 projectId，不得作为可信证据");
        runId = required(runId, "Evidence 缺失 runId，不得作为可信证据");
        sourceType = value(sourceType);
        sourceId = value(sourceId);
        toolResultId = required(toolResultId, "Evidence 缺失 toolResultId，不得作为可信证据");
        outputHash = hash(outputHash);
        fullOutputRef = required(fullOutputRef, "Evidence 缺失 fullOutputRef，不得作为可信证据");
        summary = value(summary);
        metadata = immutableMetadata(metadata);
        actor = value(actor);
    }

    private static Map<String, Object> immutableMetadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static String hash(String value) {
        String normalized = required(value, "Evidence 缺失 outputHash，不得作为可信证据").toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("EVIDENCE_OUTPUT_HASH_INVALID");
        return normalized;
    }
    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalStateException(error);
        return normalized;
    }
    private static String value(String value) { return value == null ? "" : value.trim(); }
}
