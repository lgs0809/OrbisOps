package cn.lgs.orbisops.application.agent;

public enum OpsActionStatus {
    SUCCEEDED,
    ASYNC_ACCEPTED,
    NEEDS_INPUT,
    RETRYABLE_FAILURE,
    FAILED,
    BLOCKED,
    NEEDS_HUMAN_ACTION,
    NEEDS_REPLAN,
    UNKNOWN_SIDE_EFFECT
}
