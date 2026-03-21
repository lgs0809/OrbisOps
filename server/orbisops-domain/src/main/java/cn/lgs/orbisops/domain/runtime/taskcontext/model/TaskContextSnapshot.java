package cn.lgs.orbisops.domain.runtime.taskcontext.model;

import java.time.LocalDateTime;

public record TaskContextSnapshot(
        Long persistenceId,
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        TaskContextState state,
        TaskContextContent content,
        String summary,
        Long lastEventId,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TaskContextSnapshot {
        runId = required(runId, "TASK_CONTEXT_RUN_ID_REQUIRED");
        sessionId = text(sessionId);
        projectId = text(projectId);
        agentId = text(agentId);
        if (state == null) throw new IllegalArgumentException("TASK_CONTEXT_STATE_REQUIRED");
        content = content == null ? TaskContextContent.empty() : content;
        summary = text(summary);
        if (version < 1) throw new IllegalArgumentException("TASK_CONTEXT_VERSION_INVALID");
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("TASK_CONTEXT_TIME_REQUIRED");
        }
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
