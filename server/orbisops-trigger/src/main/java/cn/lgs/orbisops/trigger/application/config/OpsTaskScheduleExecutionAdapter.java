package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionPort;
import cn.lgs.orbisops.trigger.job.AgentTaskExecutionService;

/** Scheduler runtime submission adapter for manual task execution. */
public final class OpsTaskScheduleExecutionAdapter implements TaskScheduleExecutionPort {

    private final AgentTaskExecutionService executionService;
    private final OpsTaskScheduleRuntimeConfigurationCodec codec;

    public OpsTaskScheduleExecutionAdapter(
            AgentTaskExecutionService executionService,
            OpsTaskScheduleRuntimeConfigurationCodec codec) {
        if (executionService == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_EXECUTION_SERVICE_REQUIRED");
        }
        if (codec == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_RUNTIME_CODEC_REQUIRED");
        }
        this.executionService = executionService;
        this.codec = codec;
    }

    @Override
    public Long submit(TaskScheduleDefinition schedule, String triggerType) {
        if (schedule == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_DEFINITION_REQUIRED");
        }
        if (schedule.createdBy() == null || schedule.createdBy().isBlank()) {
            throw new IllegalArgumentException("TASK_SCHEDULE_CREATOR_MISSING");
        }
        return executionService.submitExecution(new ScheduledTaskExecutionCommand(
                schedule.id(),
                schedule.taskName(),
                schedule.agentId(),
                triggerType,
                codec.encode(schedule.runtimeConfiguration()), schedule.createdBy()));
    }
}
