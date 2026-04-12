package cn.lgs.orbisops.application.analysis;

/** Audit boundary for successful and failed asynchronous analysis completion. */
public interface AsyncAnalysisAuditPort<Q, R> {

    void succeeded(Q request, R response, long durationMs);

    void failed(Q request, Exception error, long durationMs);
}
