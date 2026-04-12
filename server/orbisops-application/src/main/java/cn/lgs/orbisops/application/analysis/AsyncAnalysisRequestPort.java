package cn.lgs.orbisops.application.analysis;

/** Request protocol boundary for run identity binding and project ownership. */
public interface AsyncAnalysisRequestPort<Q> {

    void bindRunId(Q request, String runId);

    String projectId(Q request);
}
