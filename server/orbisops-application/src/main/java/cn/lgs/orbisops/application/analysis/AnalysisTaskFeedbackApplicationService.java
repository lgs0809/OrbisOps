package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisFeedbackRepository;
import cn.lgs.orbisops.domain.analysis.model.AnalysisFeedbackType;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskFeedback;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;

import java.time.Clock;
import java.util.function.Supplier;

public final class AnalysisTaskFeedbackApplicationService {

    private final AnalysisTaskQueryApplicationService queries;
    private final IAnalysisFeedbackRepository feedback;
    private final AnalysisTaskOutcomePort outcome;
    private final AnalysisTaskAuditPort audit;
    private final Supplier<String> feedbackIdSupplier;
    private final Clock clock;
    private final AnalysisTaskTransactionPort transactions;

    public AnalysisTaskFeedbackApplicationService(
            AnalysisTaskQueryApplicationService queries,
            IAnalysisFeedbackRepository feedback,
            AnalysisTaskOutcomePort outcome,
            AnalysisTaskAuditPort audit,
            Supplier<String> feedbackIdSupplier,
            Clock clock,
            AnalysisTaskTransactionPort transactions) {
        if (queries == null) throw new IllegalArgumentException("ANALYSIS_TASK_QUERY_SERVICE_REQUIRED");
        if (feedback == null) throw new IllegalArgumentException("ANALYSIS_FEEDBACK_REPOSITORY_REQUIRED");
        if (outcome == null) throw new IllegalArgumentException("ANALYSIS_TASK_OUTCOME_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("ANALYSIS_TASK_AUDIT_PORT_REQUIRED");
        if (feedbackIdSupplier == null) throw new IllegalArgumentException("ANALYSIS_FEEDBACK_ID_SUPPLIER_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("ANALYSIS_TASK_TRANSACTION_PORT_REQUIRED");
        this.queries = queries;
        this.feedback = feedback;
        this.outcome = outcome;
        this.audit = audit;
        this.feedbackIdSupplier = feedbackIdSupplier;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.transactions = transactions;
    }

    public AnalysisTaskFeedback record(
            String projectId,
            String runId,
            String feedbackType,
            String comment,
            String actor) {
        AnalysisTaskView task = queries.requireTask(projectId, runId);
        AnalysisTaskFeedback item = new AnalysisTaskFeedback(
                feedbackIdSupplier.get(),
                task.projectId(),
                task.runId(),
                AnalysisFeedbackType.require(feedbackType),
                text(comment),
                required(actor, "ANALYSIS_FEEDBACK_ACTOR_REQUIRED"),
                clock.instant());
        return transactions.required(() -> {
            AnalysisTaskFeedback saved = feedback.save(item);
            outcome.reconcile(saved.projectId(), saved.runId(), saved.negative(), saved.evidenceSufficient());
            audit.recordFeedback(task, saved);
            return saved;
        });
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
