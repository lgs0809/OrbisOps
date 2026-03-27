package cn.lgs.orbisops.application.runtime.workflow;

import java.time.Instant;

public record WorkflowApprovalRecord(
        String approvalId,
        String runId,
        String projectId,
        String nodeId,
        String waitTokenHash,
        String approveActionHash,
        String rejectActionHash,
        Status status,
        String channelId,
        String target,
        String requestSummary,
        Instant requestedAt,
        Instant expiresAt,
        String decidedBy,
        Instant decidedAt) {

    public WorkflowApprovalRecord {
        approvalId = required(approvalId, "WORKFLOW_APPROVAL_ID_REQUIRED");
        runId = required(runId, "WORKFLOW_APPROVAL_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORKFLOW_APPROVAL_PROJECT_ID_REQUIRED");
        nodeId = required(nodeId, "WORKFLOW_APPROVAL_NODE_ID_REQUIRED");
        waitTokenHash = required(waitTokenHash, "WORKFLOW_APPROVAL_WAIT_TOKEN_REQUIRED");
        approveActionHash = required(approveActionHash, "WORKFLOW_APPROVAL_APPROVE_ACTION_REQUIRED");
        rejectActionHash = required(rejectActionHash, "WORKFLOW_APPROVAL_REJECT_ACTION_REQUIRED");
        status = status == null ? Status.WAITING : status;
        channelId = text(channelId);
        target = text(target);
        requestSummary = text(requestSummary);
        if (requestedAt == null) throw new IllegalArgumentException("WORKFLOW_APPROVAL_REQUESTED_AT_REQUIRED");
        if (expiresAt == null || !expiresAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("WORKFLOW_APPROVAL_EXPIRY_INVALID");
        }
        decidedBy = text(decidedBy);
    }

    public boolean expired(Instant now) {
        Instant reference = now == null ? Instant.now() : now;
        return !expiresAt.isAfter(reference);
    }

    public boolean terminal() {
        return status != Status.WAITING;
    }

    public enum Status {
        WAITING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    public enum Decision {
        APPROVE,
        REJECT
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
