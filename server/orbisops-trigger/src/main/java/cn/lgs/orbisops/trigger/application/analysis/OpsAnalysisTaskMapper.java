package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.AnalysisTaskDetail;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskIncident;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsAnalysisTaskMapper {

    public Map<String, Object> taskView(AnalysisTaskView task) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", task.runId());
        view.put("projectId", task.projectId());
        view.put("sessionId", task.sessionId());
        view.put("userId", task.userId());
        view.put("agentId", task.agentId());
        view.put("agentVersion", task.agentVersion());
        view.put("agentDefinitionHash", task.agentDefinitionHash());
        view.put("executionHarness", task.executionHarness());
        view.put("status", task.status());
        view.put("source", task.source());
        view.put("taskType", task.taskType());
        view.put("goal", task.goal());
        view.put("response", task.response());
        view.put("errorMessage", task.errorMessage());
        view.put("technicalError", task.technicalError());
        view.put("createdAt", task.createdAt());
        view.put("updatedAt", task.updatedAt());
        return view;
    }

    public Map<String, Object> detailView(AnalysisTaskDetail detail) {
        Map<String, Object> view = new LinkedHashMap<>(taskView(detail.task()));
        view.put("events", detail.events().stream().map(this::eventView).toList());
        view.put("evidence", detail.evidence());
        view.put("toolResults", detail.toolResults());
        view.put("changePackages", detail.changePackages());
        view.put("incidents", detail.incidents().stream().map(this::incidentView).toList());
        view.put("skillUsages", detail.skillUsages());
        view.put("feedback", detail.feedback().stream().map(this::feedbackHistoryView).toList());
        view.put("hypotheses", detail.hypotheses());
        view.put("excludedFindings", detail.excludedFindings());
        view.put("unknowns", detail.unknowns());
        view.put("recommendations", detail.recommendations());
        view.put("evidenceSufficient", detail.evidenceSufficient());
        view.put("summary", detail.summary());
        return view;
    }

    public Map<String, Object> feedbackView(AnalysisTaskFeedback feedback) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("feedbackId", feedback.feedbackId());
        view.put("projectId", feedback.projectId());
        view.put("runId", feedback.runId());
        view.put("feedbackType", feedback.feedbackType().name());
        view.put("comment", feedback.comment());
        view.put("actor", feedback.actor());
        return view;
    }

    private Map<String, Object> feedbackHistoryView(AnalysisTaskFeedback feedback) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("feedbackId", feedback.feedbackId());
        view.put("feedbackType", feedback.feedbackType().name());
        view.put("commentText", feedback.comment());
        view.put("createdBy", feedback.actor());
        view.put("createdAt", feedback.createdAt());
        return view;
    }

    private Map<String, Object> incidentView(AnalysisTaskIncident incident) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("incidentId", incident.incidentId());
        view.put("title", incident.title());
        view.put("status", incident.status());
        view.put("severity", incident.severity());
        view.put("serviceName", incident.serviceName());
        view.put("occurrenceCount", incident.occurrenceCount());
        view.put("firstSeenAt", incident.firstSeenAt());
        view.put("lastSeenAt", incident.lastSeenAt());
        return view;
    }

    private Map<String, Object> eventView(GraphEvent event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", event.runId());
        view.put("sequence", event.sequence());
        view.put("eventType", event.eventType());
        view.put("nodeId", event.nodeId());
        view.put("nodeType", event.nodeType());
        view.put("agent", event.agent());
        view.put("source", event.source());
        view.put("status", event.status());
        view.put("summary", event.summary());
        view.put("startedAt", event.startedAt());
        view.put("finishedAt", event.finishedAt());
        view.put("durationMs", event.durationMs());
        view.put("payload", event.payload() == null ? Map.of() : event.payload());
        return view;
    }
}
