package cn.lgs.orbisops.domain.evidence.model;

public record ToolResult(
        String resultId,
        String projectId,
        String sessionId,
        String runId,
        String userId,
        String toolsetId,
        String toolName,
        String source,
        String status,
        String query,
        String inputHash,
        String preview,
        String fullOutput,
        String fullOutputRef,
        String outputHash,
        boolean truncated,
        long durationMs,
        ToolResultBudget budget,
        String createdBy,
        String createdAt) {

    public ToolResult {
        resultId = required(resultId, "TOOL_RESULT_ID_REQUIRED");
        projectId = value(projectId);
        sessionId = value(sessionId);
        runId = value(runId);
        userId = value(userId);
        toolsetId = value(toolsetId);
        toolName = value(toolName);
        source = value(source);
        status = required(status, "TOOL_RESULT_STATUS_REQUIRED").toUpperCase();
        query = raw(query);
        inputHash = hash(inputHash, "TOOL_RESULT_INPUT_HASH_INVALID");
        preview = raw(preview);
        fullOutput = raw(fullOutput);
        fullOutputRef = required(fullOutputRef, "TOOL_RESULT_FULL_OUTPUT_REF_REQUIRED");
        outputHash = hash(outputHash, "TOOL_RESULT_OUTPUT_HASH_INVALID");
        durationMs = Math.max(0L, durationMs);
        budget = budget == null ? ToolResultBudget.defaults() : budget;
        createdBy = value(createdBy);
        createdAt = required(createdAt, "TOOL_RESULT_CREATED_AT_REQUIRED");
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
    private static String raw(String value) { return value == null ? "" : value; }
}
