package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisFeedbackRepository;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisTaskReadRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.analysis.service.AnalysisTaskPresentationPolicy;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnalysisTaskQueryApplicationService {

    private final IAnalysisTaskReadRepository tasks;
    private final IAnalysisFeedbackRepository feedback;
    private final AnalysisTaskSupplementPort supplements;
    private final AnalysisTaskPresentationPolicy policy;

    public AnalysisTaskQueryApplicationService(
            IAnalysisTaskReadRepository tasks,
            IAnalysisFeedbackRepository feedback,
            AnalysisTaskSupplementPort supplements) {
        if (tasks == null) throw new IllegalArgumentException("ANALYSIS_TASK_REPOSITORY_REQUIRED");
        if (feedback == null) throw new IllegalArgumentException("ANALYSIS_FEEDBACK_REPOSITORY_REQUIRED");
        if (supplements == null) throw new IllegalArgumentException("ANALYSIS_TASK_SUPPLEMENT_PORT_REQUIRED");
        this.tasks = tasks;
        this.feedback = feedback;
        this.supplements = supplements;
        this.policy = new AnalysisTaskPresentationPolicy();
    }

    public List<AnalysisTaskView> list(String projectId, String source, String status, int limit) {
        String project = required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED");
        String normalizedSource = text(source).toUpperCase(Locale.ROOT);
        String normalizedStatus = text(status).isBlank()
                ? ""
                : AnalysisRunStatus.require(status).name();
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return tasks.list(project, normalizedStatus, boundedLimit).stream()
                .map(policy::present)
                .filter(task -> normalizedSource.isBlank() || normalizedSource.equalsIgnoreCase(task.source()))
                .toList();
    }

    public AnalysisTaskDetail detail(String projectId, String runId) {
        String project = required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED");
        String run = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        AnalysisTaskView task = requireTask(project, run);
        List<GraphEvent> events = safe(supplements.events(run, 500));
        List<Map<String, Object>> evidence = safe(supplements.evidence(project, run, 500));
        List<Map<String, Object>> toolResults = safe(supplements.toolResults(project, run, 500));
        List<Map<String, Object>> packages = task.sessionId().isBlank()
                ? List.of()
                : safe(supplements.changePackages(project, task.sessionId(), 100));
        List<AnalysisTaskIncident> incidents = safe(tasks.findIncidents(project, run));
        List<Map<String, Object>> skillUsages = safe(supplements.skillUsages(project, run));
        List<AnalysisTaskFeedback> feedbackItems = safe(feedback.list(project, run));
        return new AnalysisTaskDetail(
                task,
                events,
                evidence,
                toolResults,
                packages,
                incidents,
                skillUsages,
                feedbackItems,
                policy.summaries(events, "HYPOTHESIS", "DIAGNOSIS", "ROOT_CAUSE"),
                policy.summaries(events, "EXCLUDED", "REJECTED_HYPOTHESIS"),
                policy.statusSummaries(events, "UNKNOWN", "INSUFFICIENT", "BLOCKED"),
                policy.summaries(events, "RECOMMEND", "REPLAN", "CHANGE_PACKAGE"),
                !evidence.isEmpty(),
                policy.responseSummary(task));
    }

    public AnalysisTaskView requireTask(String projectId, String runId) {
        String project = required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED");
        String run = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        AnalysisTaskSnapshot snapshot = tasks.find(project, run)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Analysis Task 不存在或不属于当前项目：" + run));
        return policy.present(snapshot);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
