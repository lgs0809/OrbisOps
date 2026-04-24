package cn.lgs.orbisops.application.schedule;

/** Command used to create or update one scheduled Agent task. */
public record TaskScheduleCommand(
        Long id,
        String projectId,
        String executionType,
        String agentId,
        String agentBindingMode,
        Integer agentVersion,
        String taskName,
        String description,
        String cronExpression,
        String prompt,
        Integer status,
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
        Boolean lightweightScreeningEnabled,
        String screeningSourceType,
        String screeningPrimaryUri,
        Double maxErrorRatePercent,
        Double maxCpuPercent,
        Double maxHeapPercent,
        Double minInstanceUpRatio) {

    /** Backward-compatible constructor for callers created before task-level screening recipes. */
    public TaskScheduleCommand(
            Long id,
            String projectId,
            String agentId,
            String agentBindingMode,
            Integer agentVersion,
            String taskName,
            String description,
            String cronExpression,
            String prompt,
            Integer status,
            Integer rangeMinutes,
            String promWindow,
            Boolean includeRecentLogs,
            Integer maxRounds,
            Integer subAgentMaxIterations,
            Integer nodeTimeoutSeconds,
            Integer maxEvidenceItems,
            Boolean notifyChannel,
            String notificationChannelId,
            String notificationTarget) {
        this(id, projectId, null, agentId, agentBindingMode, agentVersion, taskName, description,
                cronExpression, prompt, status, rangeMinutes, promWindow, includeRecentLogs,
                maxRounds, subAgentMaxIterations, nodeTimeoutSeconds, maxEvidenceItems,
                notifyChannel, notificationChannelId, notificationTarget,
                false, "PROMETHEUS", "", null, null, null, null);
    }
}
