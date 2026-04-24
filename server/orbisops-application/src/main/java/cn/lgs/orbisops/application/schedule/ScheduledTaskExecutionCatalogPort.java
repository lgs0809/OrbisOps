package cn.lgs.orbisops.application.schedule;

/** Persistence boundary for scheduled task execution lifecycle records. */
public interface ScheduledTaskExecutionCatalogPort {

    void ensureStorage();

    Long create(ScheduledTaskExecutionCommand command);

    void updateInput(Long executionId, String input);

    void markSucceeded(Long executionId, String output);

    void markFailed(Long executionId, String errorMessage, String output);

    void markIncomplete(Long executionId, String status, String output);

    int reconcileWaitingRuns();
}
