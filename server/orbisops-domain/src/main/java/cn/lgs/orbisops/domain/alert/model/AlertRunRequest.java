package cn.lgs.orbisops.domain.alert.model;

public record AlertRunRequest(
        String runId,
        String requestedBy,
        String projectId,
        String agentDefinitionId,
        Integer agentVersion,
        String agentDefinitionSnapshotJson,
        String query,
        String question,
        Integer rangeMinutes,
        String promWindow,
        Boolean includeRecentLogs,
        Integer maxRounds,
        Integer subAgentMaxIterations,
        Integer nodeTimeoutSeconds,
        Integer maxEvidenceItems,
        Boolean notifyChannel,
        String notificationChannelId,
        String notificationTarget,
        String executionStyle,
        String triggerSource,
        String triggerEventId) {

    public AlertRunRequest {
        runId = text(runId);
        requestedBy = required(requestedBy, "ALERT_OUTBOX_REQUESTED_BY_REQUIRED");
        projectId = required(projectId, "ALERT_OUTBOX_PROJECT_ID_REQUIRED");
        agentDefinitionId = required(agentDefinitionId, "ALERT_OUTBOX_AGENT_ID_REQUIRED");
        agentDefinitionSnapshotJson = text(agentDefinitionSnapshotJson);
        query = text(query);
        question = text(question);
        promWindow = text(promWindow);
        notificationChannelId = text(notificationChannelId);
        notificationTarget = text(notificationTarget);
        executionStyle = text(executionStyle);
        triggerSource = text(triggerSource);
        triggerEventId = text(triggerEventId);
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
