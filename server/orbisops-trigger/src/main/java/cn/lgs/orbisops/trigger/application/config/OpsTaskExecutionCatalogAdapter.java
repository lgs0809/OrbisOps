package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.TaskExecutionView;
import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskExecutionRepository;
import cn.lgs.orbisops.domain.agent.model.entity.TaskExecutionRecord;

import java.util.List;

/** Repository query adapter for scheduled task execution history. */
public final class OpsTaskExecutionCatalogAdapter implements TaskExecutionCatalogPort {

    private final ITaskExecutionRepository repository;

    public OpsTaskExecutionCatalogAdapter(ITaskExecutionRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("TASK_EXECUTION_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public List<TaskExecutionView> list(Long scheduleId, int limit) {
        List<TaskExecutionRecord> values = repository.list(scheduleId, limit);
        return values == null || values.isEmpty()
                ? List.of()
                : values.stream().map(this::view).toList();
    }

    private TaskExecutionView view(TaskExecutionRecord record) {
        return new TaskExecutionView(
                record.getId(),
                record.getScheduleId(),
                record.getTaskName(),
                record.getAgentId(),
                record.getTriggerType(),
                record.getStatus(),
                record.getStartedAt(),
                record.getEndedAt(),
                record.getInput(),
                record.getOutput(),
                record.getErrorMessage());
    }
}
