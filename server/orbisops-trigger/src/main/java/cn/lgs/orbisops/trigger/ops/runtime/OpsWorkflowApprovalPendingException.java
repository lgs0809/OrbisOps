package cn.lgs.orbisops.trigger.ops.runtime;

/** Non-failure control signal: durable workflow intentionally paused for an authoritative human decision. */
final class OpsWorkflowApprovalPendingException extends RuntimeException {

    private final String nodeId;
    private final String approvalId;

    OpsWorkflowApprovalPendingException(String nodeId, String approvalId) {
        super("WORKFLOW_APPROVAL_WAITING:" + text(nodeId));
        this.nodeId = text(nodeId);
        this.approvalId = text(approvalId);
        if (this.nodeId.isBlank() || this.approvalId.isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_APPROVAL_PENDING_IDENTITY_REQUIRED");
        }
    }

    String nodeId() {
        return nodeId;
    }

    String approvalId() {
        return approvalId;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
