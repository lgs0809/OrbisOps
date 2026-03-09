package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;

import java.util.List;
import java.util.Map;

public record AnalysisTaskDetail(
        AnalysisTaskView task,
        List<GraphEvent> events,
        List<Map<String, Object>> evidence,
        List<Map<String, Object>> toolResults,
        List<Map<String, Object>> changePackages,
        List<AnalysisTaskIncident> incidents,
        List<Map<String, Object>> skillUsages,
        List<AnalysisTaskFeedback> feedback,
        List<String> hypotheses,
        List<String> excludedFindings,
        List<String> unknowns,
        List<String> recommendations,
        boolean evidenceSufficient,
        String summary) {

    public AnalysisTaskDetail {
        if (task == null) throw new IllegalArgumentException("ANALYSIS_TASK_REQUIRED");
        events = safe(events);
        evidence = safe(evidence);
        toolResults = safe(toolResults);
        changePackages = safe(changePackages);
        incidents = safe(incidents);
        skillUsages = safe(skillUsages);
        feedback = safe(feedback);
        hypotheses = safe(hypotheses);
        excludedFindings = safe(excludedFindings);
        unknowns = safe(unknowns);
        recommendations = safe(recommendations);
        summary = summary == null ? "" : summary.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
