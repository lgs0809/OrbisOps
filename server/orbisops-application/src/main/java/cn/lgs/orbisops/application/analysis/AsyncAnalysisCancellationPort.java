package cn.lgs.orbisops.application.analysis;

/** Cooperative cancellation boundary for asynchronous analysis work. */
public interface AsyncAnalysisCancellationPort {

    void markCanceled(String runId);

    boolean isCanceled(String runId);

    void assertNotCanceled(String runId);

    void markFinished(String runId);
}
