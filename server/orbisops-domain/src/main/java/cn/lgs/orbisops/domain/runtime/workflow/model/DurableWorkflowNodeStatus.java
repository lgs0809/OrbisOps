package cn.lgs.orbisops.domain.runtime.workflow.model;

public enum DurableWorkflowNodeStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELED
}
