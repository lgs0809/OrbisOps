package cn.lgs.orbisops.application.schedule;

/** Typed runtime configuration persisted with one scheduled Agent task. */
public record TaskScheduleRuntimeConfiguration(
        String projectId,
        String executionType,
        String agentBindingMode,
        Integer agentVersion,
        String agentDefinitionHash,
        String prompt,
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
        TaskScheduleScreeningConfiguration screening) {

    public TaskScheduleRuntimeConfiguration {
        screening = screening == null ? TaskScheduleScreeningConfiguration.disabled() : screening;
    }

    /** Backward-compatible constructor for callers that predate the explicit execution type. */
    public TaskScheduleRuntimeConfiguration(
            String projectId,
            String agentBindingMode,
            Integer agentVersion,
            String agentDefinitionHash,
            String prompt,
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
            TaskScheduleScreeningConfiguration screening) {
        this(projectId, null, agentBindingMode, agentVersion, agentDefinitionHash, prompt, rangeMinutes,
                promWindow, includeRecentLogs, maxRounds, subAgentMaxIterations, nodeTimeoutSeconds,
                maxEvidenceItems, notifyChannel, notificationChannelId, notificationTarget, screening);
    }

    /** Backward-compatible constructor for persisted/runtime callers created before screening recipes. */
    public TaskScheduleRuntimeConfiguration(
            String projectId,
            String agentBindingMode,
            Integer agentVersion,
            String agentDefinitionHash,
            String prompt,
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
        this(projectId, null, agentBindingMode, agentVersion, agentDefinitionHash, prompt, rangeMinutes,
                promWindow, includeRecentLogs, maxRounds, subAgentMaxIterations, nodeTimeoutSeconds,
                maxEvidenceItems, notifyChannel, notificationChannelId, notificationTarget,
                TaskScheduleScreeningConfiguration.disabled());
    }
}
