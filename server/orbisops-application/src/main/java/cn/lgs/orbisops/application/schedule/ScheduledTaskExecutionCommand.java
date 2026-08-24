package cn.lgs.orbisops.application.schedule;

/** Typed command for one scheduled Agent task execution with an opaque runtime payload. */
public record ScheduledTaskExecutionCommand(
        Long scheduleId,
        String taskName,
        String agentId,
        String triggerType,
        String runtimePayload,
        String createdBy) {
    public ScheduledTaskExecutionCommand(Long scheduleId, String taskName, String agentId,
            String triggerType, String runtimePayload) {
        this(scheduleId, taskName, agentId, triggerType, runtimePayload, null);
    }
}
