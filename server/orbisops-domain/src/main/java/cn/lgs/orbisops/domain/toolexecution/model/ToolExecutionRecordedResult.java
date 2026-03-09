package cn.lgs.orbisops.domain.toolexecution.model;

public record ToolExecutionRecordedResult(
        String resultId,
        String evidenceId,
        String preview,
        String outputHash,
        boolean truncated,
        String fullOutputRef,
        String inputHash,
        long durationMs) {

    public ToolExecutionRecordedResult {
        resultId = required(resultId, "TOOL_EXECUTION_RESULT_ID_REQUIRED");
        evidenceId = required(evidenceId, "TOOL_EXECUTION_EVIDENCE_ID_REQUIRED");
        preview = preview == null ? "" : preview;
        outputHash = hash(outputHash, "TOOL_EXECUTION_OUTPUT_HASH_INVALID");
        fullOutputRef = required(fullOutputRef, "TOOL_EXECUTION_FULL_OUTPUT_REF_REQUIRED");
        inputHash = hash(inputHash, "TOOL_EXECUTION_INPUT_HASH_INVALID");
        durationMs = Math.max(0L, durationMs);
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

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
