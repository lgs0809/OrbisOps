package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionPort;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;
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
        TaskScheduleRecord record = TaskScheduleRecord.builder()
                .id(schedule.id())
                .projectId(schedule.projectId())
                .agentId(schedule.agentId())
                .taskName(schedule.taskName())
                .description(schedule.description())
                .cronExpression(schedule.cronExpression())
                .taskParam(codec.encode(schedule.runtimeConfiguration()))
                .status(schedule.status())
                .createTime(schedule.createTime())
                .updateTime(schedule.updateTime())
                .build();
        return executionService.submitExecution(record, triggerType);
    }
}
