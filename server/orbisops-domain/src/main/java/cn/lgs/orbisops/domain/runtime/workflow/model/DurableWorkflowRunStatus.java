package cn.lgs.orbisops.domain.runtime.workflow.model;

public enum DurableWorkflowRunStatus {
    READY,
    RUNNING,
    WAITING,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELED,
    RECOVERY_REVIEW_REQUIRED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED
                || this == RECOVERY_REVIEW_REQUIRED;
    }
}
