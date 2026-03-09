package cn.lgs.orbisops.application.schedule;

/** Executor boundary for scheduled task execution. */
public interface ScheduledTaskExecutionExecutorPort {

    void execute(Runnable task);
}
