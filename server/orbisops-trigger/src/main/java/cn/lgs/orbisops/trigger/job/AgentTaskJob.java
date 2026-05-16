package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistration;
import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistryPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionUseCase;
import cn.lgs.orbisops.trigger.scheduling.model.TaskScheduleVO;
import cn.lgs.orbisops.trigger.scheduling.provider.ITaskDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Scheduler inbound adapter backed by typed Application schedule boundaries. */
@Slf4j
@Service
public class AgentTaskJob implements ITaskDataProvider {

    private final ScheduledTaskRegistryPort registryPort;
    private final TaskScheduleExecutionUseCase executionUseCase;

    public AgentTaskJob(
            ScheduledTaskRegistryPort registryPort,
            TaskScheduleExecutionUseCase executionUseCase) {
        if (registryPort == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_REGISTRY_PORT_REQUIRED");
        }
        if (executionUseCase == null) {
            throw new IllegalArgumentException("TASK_SCHEDULE_EXECUTION_USE_CASE_REQUIRED");
        }
        this.registryPort = registryPort;
        this.executionUseCase = executionUseCase;
    }

    @Override
    public List<TaskScheduleVO> queryAllValidTaskSchedule() {
        List<ScheduledTaskRegistration> schedules = registryPort.listEnabled();
        List<TaskScheduleVO> result = new ArrayList<>();
        for (ScheduledTaskRegistration schedule : schedules) {
            TaskScheduleVO task = new TaskScheduleVO();
            task.setId(schedule.id());
            task.setDescription(schedule.description());
            task.setCronExpression(schedule.cronExpression());
            task.setTaskLogic(() -> executionUseCase.runScheduled(schedule.id()));
            result.add(task);
        }
        return result;
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return registryPort.listInvalidIds();
    }
}
