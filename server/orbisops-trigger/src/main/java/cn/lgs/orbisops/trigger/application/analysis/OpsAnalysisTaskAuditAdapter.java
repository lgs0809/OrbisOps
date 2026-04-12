package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.AnalysisTaskAuditPort;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsAnalysisTaskAuditAdapter implements AnalysisTaskAuditPort {

    private final OpsConfigAuditService audit;
    private final OpsAnalysisTaskMapper mapper;

    public OpsAnalysisTaskAuditAdapter(
            OpsConfigAuditService audit,
            OpsAnalysisTaskMapper mapper) {
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override
    public void recordFeedback(AnalysisTaskView task, AnalysisTaskFeedback feedback) {
        audit.record(
                task.projectId(),
                "analysis-task",
                "feedback",
                task.runId(),
                mapper.taskView(task),
                mapper.feedbackView(feedback));
    }
}
