package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunSnapshot;
import cn.lgs.orbisops.domain.worksession.run.model.WorkSessionRunStatus;
import cn.lgs.orbisops.trigger.application.channel.WorkflowApprovalDecidedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web command/read surface over the same durable HUMAN_APPROVAL authority used by Channel callbacks. */
@Service
public class OpsWorkflowApprovalWebApplicationService {

    private final WorkflowApprovalApplicationService approvals;
    private final WorkSessionRunApplicationService workSessions;
    private final ApplicationEventPublisher events;

    public OpsWorkflowApprovalWebApplicationService(WorkflowApprovalApplicationService approvals,
                                                     WorkSessionRunApplicationService workSessions,
                                                     ApplicationEventPublisher events) {
        if (approvals == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_SERVICE_REQUIRED");
        if (workSessions == null) throw new IllegalArgumentException("WORK_SESSION_RUN_SERVICE_REQUIRED");
        if (events == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_EVENT_PUBLISHER_REQUIRED");
        this.approvals = approvals;
        this.workSessions = workSessions;
        this.events = events;
    }

    public Map<String, Object> view(String runId, String projectId) {
        WorkSessionRunSnapshot run = workSessions.get(required(runId, "runId"), required(projectId, "projectId"));
        return view(run, current(run.runId(), false), "");
    }

    public Map<String, Object> viewForProjectActor(String runId, String projectId, String actor) {
        String safeRunId = required(runId, "runId");
        String safeProjectId = required(projectId, "projectId");
        String safeActor = required(actor, "actor");
        return view(workSessions.get(safeRunId, safeProjectId), current(safeRunId, false), safeActor);
    }

    public Map<String, Object> viewForActor(String runId, String projectId, String actor) {
        String safeRunId = required(runId, "runId");
        String safeProjectId = required(projectId, "projectId");
        String safeActor = required(actor, "actor");
        workSessions.assertActorCanRead(safeRunId, safeProjectId, safeActor);
        return view(workSessions.get(safeRunId, safeProjectId), current(safeRunId, false), safeActor);
    }

    public Map<String, Object> decide(String runId,
                                      String projectId,
                                      String actor,
                                      String decision,
                                      String expectedApprovalId) {
        String safeRunId = required(runId, "runId");
        String safeProjectId = required(projectId, "projectId");
        String safeActor = required(actor, "actor");
        WorkflowApprovalRecord.Decision requested = decision(decision);
        WorkflowApprovalRecord current = current(safeRunId, true);
        assertProject(current, safeProjectId);
        assertReviewedApproval(current, expectedApprovalId);

        // Current run status + owner/editor authority are rechecked immediately before consuming the decision.
        WorkSessionRunSnapshot run = workSessions.resumeApproval(safeRunId, safeProjectId, safeActor);
        WorkflowApprovalRecord decided;
        if (current.status() == WorkflowApprovalRecord.Status.WAITING) {
            decided = approvals.decide(current, requested, safeActor);
        } else {
            WorkflowApprovalRecord.Decision existing = terminalDecision(current);
            if (existing != requested) {
                throw new SecurityException("WORKFLOW_APPROVAL_OPPOSITE_DECISION_FORBIDDEN");
            }
            decided = current;
        }
        publish(decided, safeActor, requested);
        return view(run, decided, safeActor);
    }

    public Map<String, Object> retryResume(String runId, String projectId, String actor, String expectedApprovalId) {
        String safeRunId = required(runId, "runId");
        String safeProjectId = required(projectId, "projectId");
        String safeActor = required(actor, "actor");
        WorkflowApprovalRecord current = current(safeRunId, true);
        assertProject(current, safeProjectId);
        assertReviewedApproval(current, expectedApprovalId);
        WorkflowApprovalRecord.Decision decision = terminalDecision(current);
        WorkSessionRunSnapshot run = workSessions.resumeApproval(safeRunId, safeProjectId, safeActor);
        publish(current, safeActor, decision);
        return view(run, current, safeActor);
    }

    private WorkflowApprovalRecord current(String runId, boolean required) {
        return approvals.findCurrent(runId).orElseGet(() -> {
            if (required) throw new IllegalStateException("WORKFLOW_APPROVAL_NOT_FOUND");
            return null;
        });
    }

    private Map<String, Object> view(WorkSessionRunSnapshot run,
                                     WorkflowApprovalRecord approval,
                                     String decisionActor) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", approval != null);
        result.put("runId", run.runId());
        result.put("projectId", run.projectId());
        result.put("runStatus", run.status().name());
        if (approval == null) return Map.copyOf(result);
        assertProject(approval, run.projectId());
        result.put("approvalId", approval.approvalId());
        result.put("nodeId", approval.nodeId());
        result.put("status", approval.status().name());
        result.put("requestSummary", approval.requestSummary());
        result.put("requestedAt", approval.requestedAt().toString());
        result.put("expiresAt", approval.expiresAt().toString());
        result.put("decidedBy", approval.decidedBy());
        result.put("decidedAt", approval.decidedAt() == null ? "" : approval.decidedAt().toString());
        result.put("channelBound", !approval.channelId().isBlank());
        boolean waitingRun = run.status() == WorkSessionRunStatus.WAITING_APPROVAL;
        boolean actorCanResume = waitingRun && canResumeApproval(run.runId(), run.projectId(), decisionActor);
        result.put("canDecide", actorCanResume && approval.status() == WorkflowApprovalRecord.Status.WAITING);
        result.put("resumeRequired", actorCanResume && approval.terminal());
        return Map.copyOf(result);
    }

    private boolean canResumeApproval(String runId, String projectId, String actor) {
        if (actor == null || actor.isBlank()) return false;
        try {
            workSessions.resumeApproval(runId, projectId, actor);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    private void publish(WorkflowApprovalRecord record,
                         String actor,
                         WorkflowApprovalRecord.Decision decision) {
        events.publishEvent(new WorkflowApprovalDecidedEvent(
                record.approvalId(), record.runId(), record.projectId(), record.nodeId(), actor, decision));
    }

    private WorkflowApprovalRecord.Decision terminalDecision(WorkflowApprovalRecord record) {
        return switch (record.status()) {
            case APPROVED -> WorkflowApprovalRecord.Decision.APPROVE;
            case REJECTED -> WorkflowApprovalRecord.Decision.REJECT;
            case WAITING -> throw new IllegalStateException("WORKFLOW_APPROVAL_DECISION_REQUIRED");
            case EXPIRED -> throw new SecurityException("WORKFLOW_APPROVAL_EXPIRED");
        };
    }

    private WorkflowApprovalRecord.Decision decision(String value) {
        String normalized = required(value, "decision").toUpperCase(java.util.Locale.ROOT);
        try {
            return WorkflowApprovalRecord.Decision.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("WORKFLOW_APPROVAL_DECISION_INVALID:" + normalized);
        }
    }

    private void assertProject(WorkflowApprovalRecord approval, String projectId) {
        if (approval == null || !approval.projectId().equals(projectId)) {
            throw new SecurityException("WORKFLOW_APPROVAL_PROJECT_MISMATCH");
        }
    }

    private void assertReviewedApproval(WorkflowApprovalRecord current, String expectedApprovalId) {
        // An approval ID identifies an immutable run/node/wait issuance. A stale browser must
        // never decide a newer node merely because it is now the current approval for the run.
        if (!current.approvalId().equals(required(expectedApprovalId, "approvalId"))) {
            throw new SecurityException("WORKFLOW_APPROVAL_REVIEWED_RECORD_MISMATCH");
        }
    }

    private String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("WORKFLOW_APPROVAL_" + name.toUpperCase() + "_REQUIRED");
        return normalized;
    }
}
