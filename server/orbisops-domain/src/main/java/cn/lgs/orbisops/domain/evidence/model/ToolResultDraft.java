package cn.lgs.orbisops.domain.evidence.model;

public record ToolResultDraft(
        String projectId,
        String sessionId,
        String runId,
        String userId,
        String toolsetId,
        String toolName,
        String source,
        String status,
        String query,
        String output,
        ToolResultBudget budget,
        String actor,
        long durationMs) {

    public ToolResultDraft {
        projectId = value(projectId);
        sessionId = value(sessionId);
        runId = value(runId);
        userId = value(userId);
        toolsetId = value(toolsetId);
        toolName = value(toolName);
        source = value(source);
        status = value(status).isBlank() ? "SUCCEEDED" : value(status).toUpperCase();
        query = raw(query);
        output = raw(output);
        budget = budget == null ? ToolResultBudget.defaults() : budget;
        actor = value(actor);
        durationMs = Math.max(0L, durationMs);
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static String raw(String value) { return value == null ? "" : value; }
}
