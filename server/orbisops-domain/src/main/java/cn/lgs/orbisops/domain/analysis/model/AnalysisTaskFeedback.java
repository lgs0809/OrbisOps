package cn.lgs.orbisops.domain.analysis.model;

import java.time.Instant;

public record AnalysisTaskFeedback(
        String feedbackId,
        String projectId,
        String runId,
        AnalysisFeedbackType feedbackType,
        String comment,
        String actor,
        Instant createdAt) {

    public AnalysisTaskFeedback {
        feedbackId = required(feedbackId, "ANALYSIS_FEEDBACK_ID_REQUIRED");
        projectId = required(projectId, "ANALYSIS_PROJECT_ID_REQUIRED");
        runId = required(runId, "ANALYSIS_RUN_ID_REQUIRED");
        if (feedbackType == null) throw new IllegalArgumentException("ANALYSIS_FEEDBACK_TYPE_REQUIRED");
        comment = text(comment);
        actor = required(actor, "ANALYSIS_FEEDBACK_ACTOR_REQUIRED");
    }

    public boolean negative() {
        return feedbackType.negative();
    }

    public boolean evidenceSufficient() {
        return feedbackType.evidenceSufficient();
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
