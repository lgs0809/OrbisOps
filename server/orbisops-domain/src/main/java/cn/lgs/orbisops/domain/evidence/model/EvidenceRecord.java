package cn.lgs.orbisops.domain.evidence.model;

import java.util.Map;

public record EvidenceRecord(
        String evidenceId,
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
        String idempotencyKey,
        String createdBy,
        String createdAt) {

    public EvidenceRecord {
        evidenceId = required(evidenceId, "EVIDENCE_ID_REQUIRED");
        projectId = required(projectId, "EVIDENCE_PROJECT_ID_REQUIRED");
        runId = required(runId, "EVIDENCE_RUN_ID_REQUIRED");
        sourceType = value(sourceType);
        sourceId = value(sourceId);
        toolResultId = required(toolResultId, "EVIDENCE_TOOL_RESULT_ID_REQUIRED");
        outputHash = hash(outputHash, "EVIDENCE_OUTPUT_HASH_INVALID");
        fullOutputRef = required(fullOutputRef, "EVIDENCE_FULL_OUTPUT_REF_REQUIRED");
        summary = value(summary);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        idempotencyKey = hash(idempotencyKey, "EVIDENCE_IDEMPOTENCY_KEY_INVALID");
        createdBy = value(createdBy);
        createdAt = required(createdAt, "EVIDENCE_CREATED_AT_REQUIRED");
    }

    public void requireScope(String expectedProjectId, String expectedRunId) {
        if (!projectId.equals(value(expectedProjectId)) || !runId.equals(value(expectedRunId))) {
            throw new SecurityException("EVIDENCE_NOT_FOUND_OR_CROSS_SCOPE：Evidence 不属于当前 project/run");
        }
    }

    public void requireComplete() {
        if (toolResultId.isBlank()) throw new SecurityException("EVIDENCE_INCOMPLETE：Evidence 缺失 toolResultId");
        if (outputHash.isBlank()) throw new SecurityException("EVIDENCE_INCOMPLETE：Evidence 缺失 outputHash");
        if (fullOutputRef.isBlank()) throw new SecurityException("EVIDENCE_INCOMPLETE：Evidence 缺失 fullOutputRef");
    }

    private static String hash(String value, String error) {
        String normalized = value(value).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(error);
        return normalized;
    }
    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
    private static String value(String value) { return value == null ? "" : value.trim(); }
}
