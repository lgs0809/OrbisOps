package cn.lgs.orbisops.domain.analysis.adapter.repository;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;

import java.util.List;

public interface IAnalysisFeedbackRepository {

    AnalysisTaskFeedback save(AnalysisTaskFeedback feedback);

    List<AnalysisTaskFeedback> list(String projectId, String runId);
}
