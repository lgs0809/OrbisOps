package cn.lgs.orbisops.application.analysis;

public interface AnalysisTaskOutcomePort {

    void reconcile(String projectId, String runId, boolean negativeFeedback, boolean evidenceSufficient);
}
