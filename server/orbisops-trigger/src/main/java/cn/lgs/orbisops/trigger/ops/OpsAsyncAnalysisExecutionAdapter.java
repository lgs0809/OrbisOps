package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.analysis.AsyncAnalysisExecutionPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/** Thread-pool admission and Future handle adapter for asynchronous analysis runs. */
public final class OpsAsyncAnalysisExecutionAdapter implements AsyncAnalysisExecutionPort {

    private final ThreadPoolExecutor executor;
    private final OpsAnalysisRunSettings settings;
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();

    public OpsAsyncAnalysisExecutionAdapter(
            ThreadPoolExecutor executor,
            OpsAnalysisRunSettings settings) {
        if (executor == null) {
            throw new IllegalArgumentException("OPS_RUN_EXECUTOR_REQUIRED");
        }
        this.executor = executor;
        this.settings = settings == null ? OpsAnalysisRunSettings.defaults() : settings;
    }

    @Override
    public void assertCapacity() {
        if (!settings.rejectWhenQueueFull()) {
            return;
        }
        boolean workersSaturated = executor.getActiveCount() >= executor.getMaximumPoolSize();
        if (workersSaturated && executor.getQueue().remainingCapacity() == 0) {
            throw new IllegalStateException("运维分析任务队列已满，请稍后重试。");
        }
    }

    @Override
    public void submit(String runId, Runnable task) {
        futures.put(runId, executor.submit(task));
    }

    @Override
    public void cancel(String runId) {
        Future<?> future = futures.get(runId);
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public boolean interrupted() {
        return Thread.currentThread().isInterrupted();
    }

    @Override
    public void finished(String runId) {
        futures.remove(runId);
    }
}
