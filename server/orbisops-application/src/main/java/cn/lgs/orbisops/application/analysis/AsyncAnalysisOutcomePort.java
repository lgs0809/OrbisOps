package cn.lgs.orbisops.application.analysis;

/** Event and alert outcome boundary for asynchronous analysis terminal states. */
public interface AsyncAnalysisOutcomePort<Q, R> {

    void succeeded(Q request, String runId, R response);

    void failed(Q request, String runId, String errorMessage);

    void canceled(Q request, String runId);
}
