package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;

public interface AnalysisTaskAuditPort {

    void recordFeedback(AnalysisTaskView task, AnalysisTaskFeedback feedback);
}
