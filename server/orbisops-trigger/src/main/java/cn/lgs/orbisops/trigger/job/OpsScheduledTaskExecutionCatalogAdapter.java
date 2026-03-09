package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskExecutionRepository;

/** Repository adapter for scheduled task execution lifecycle records. */
public final class OpsScheduledTaskExecutionCatalogAdapter implements ScheduledTaskExecutionCatalogPort {

    private final ITaskExecutionRepository repository;

    public OpsScheduledTaskExecutionCatalogAdapter(ITaskExecutionRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("TASK_EXECUTION_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public void ensureStorage() {
        repository.ensureTable();
    }

    @Override
    public Long create(ScheduledTaskExecutionCommand command) {
        return repository.create(
                command.scheduleId(),
                command.taskName(),
                command.agentId(),
                command.triggerType());
    }

    @Override
    public void updateInput(Long executionId, String input) {
        repository.updateInput(executionId, input);
    }

    @Override
    public void markSucceeded(Long executionId, String output) {
        repository.markSuccess(executionId, output);
    }

    @Override
    public void markFailed(Long executionId, String errorMessage, String output) {
        repository.markFailed(executionId, errorMessage, output);
    }
}
