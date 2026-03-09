package cn.lgs.orbisops.domain.runtime.workflow.model;

public enum DurableWorkflowWaitType {
    NONE,
    TIMER,
    EXTERNAL_SIGNAL,
    HUMAN_APPROVAL
}
