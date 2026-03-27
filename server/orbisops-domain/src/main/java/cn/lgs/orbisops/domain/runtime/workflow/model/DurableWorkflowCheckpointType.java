package cn.lgs.orbisops.domain.runtime.workflow.model;

public enum DurableWorkflowCheckpointType {
    PLAN_BOUND,
    RUN_STARTED,
    NODE_BEFORE,
    NODE_AFTER,
    NODE_FAILED,
    ROUTE_SELECTED,
    LOOP_INCREMENTED,
    TOOL_BEFORE,
    TOOL_AFTER,
    WAITING,
    APPROVAL_WAITING,
    APPROVAL_RESUMED,
    RUN_SUCCEEDED,
    RUN_FAILED,
    RUN_CANCELED,
    RECOVERED
}
