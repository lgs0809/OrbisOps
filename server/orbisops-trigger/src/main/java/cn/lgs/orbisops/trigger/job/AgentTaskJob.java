package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.domain.agent.model.valobj.AiAgentTaskScheduleVO;
import cn.lgs.orbisops.domain.agent.service.ITaskService;
import cn.lgs.orbisops.trigger.scheduling.model.TaskScheduleVO;
import cn.lgs.orbisops.trigger.scheduling.provider.ITaskDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Scheduler data provider for enabled Agent tasks. */
@Slf4j
@Service
public class AgentTaskJob implements ITaskDataProvider {

    private final ITaskService taskService;
    private final AgentTaskExecutionService agentTaskExecutionService;

    public AgentTaskJob(
            ITaskService taskService,
            AgentTaskExecutionService agentTaskExecutionService) {
        if (taskService == null) {
            throw new IllegalArgumentException("TASK_SERVICE_REQUIRED");
        }
        if (agentTaskExecutionService == null) {
            throw new IllegalArgumentException("AGENT_TASK_EXECUTION_SERVICE_REQUIRED");
        }
        this.taskService = taskService;
        this.agentTaskExecutionService = agentTaskExecutionService;
    }

    @Override
    public List<TaskScheduleVO> queryAllValidTaskSchedule() {
        List<AiAgentTaskScheduleVO> schedules = taskService.queryAllValidTaskSchedule();
        List<TaskScheduleVO> result = new ArrayList<>();
        for (AiAgentTaskScheduleVO schedule : schedules) {
            TaskScheduleVO task = new TaskScheduleVO();
            task.setId(schedule.getId());
            task.setDescription(schedule.getDescription());
            task.setCronExpression(schedule.getCronExpression());
            task.setTaskParam(schedule.getTaskParam());
            task.setTaskLogic(() -> agentTaskExecutionService.submitExecution(schedule, "SCHEDULED"));
            result.add(task);
        }
        return result;
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return taskService.queryAllInvalidTaskScheduleIds();
    }
}
