package cn.lgs.orbisops.domain.memory.model;

/** Typed runtime-selection criteria for governed explicit memories. */
public record GovernedMemoryRuntimeQuery(
        String userId,
        String projectId,
        String sessionId,
        String excludedSourceRunId,
        int limit) {

    public GovernedMemoryRuntimeQuery {
        userId = value(userId);
        projectId = value(projectId);
        sessionId = value(sessionId);
        excludedSourceRunId = value(excludedSourceRunId);
        limit = Math.max(1, Math.min(limit, 50));
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
