package cn.lgs.orbisops.application.analysis;

/** Executor and task-handle boundary for asynchronous analysis runs. */
public interface AsyncAnalysisExecutionPort {

    void assertCapacity();

    void submit(String runId, Runnable task);

    void cancel(String runId);

    boolean interrupted();

    void finished(String runId);
}
