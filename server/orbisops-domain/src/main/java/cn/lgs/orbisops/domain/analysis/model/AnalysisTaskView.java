package cn.lgs.orbisops.domain.analysis.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AnalysisTaskView(
        String runId,
        String projectId,
        String sessionId,
        String userId,
        String agentId,
        int agentVersion,
        String agentDefinitionHash,
        String executionHarness,
        String status,
        String source,
        String taskType,
        String goal,
        Map<String, Object> response,
        String errorMessage,
        String technicalError,
        Instant createdAt,
        Instant updatedAt) {

    public AnalysisTaskView {
        runId = text(runId);
        projectId = text(projectId);
        sessionId = text(sessionId);
        userId = text(userId);
        agentId = text(agentId);
        agentDefinitionHash = text(agentDefinitionHash);
        executionHarness = text(executionHarness);
        status = text(status);
        source = text(source);
        taskType = text(taskType);
        goal = text(goal);
        response = response == null || response.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(response));
        errorMessage = text(errorMessage);
        technicalError = text(technicalError);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
