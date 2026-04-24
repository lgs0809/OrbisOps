package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionExecutorPort;

import java.util.concurrent.ThreadPoolExecutor;

/** Thread-pool adapter for scheduled task execution. */
public final class OpsScheduledTaskExecutionExecutorAdapter implements ScheduledTaskExecutionExecutorPort {

    private final ThreadPoolExecutor executor;

    public OpsScheduledTaskExecutionExecutorAdapter(ThreadPoolExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("OPS_RUN_EXECUTOR_REQUIRED");
        }
        this.executor = executor;
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }
}
