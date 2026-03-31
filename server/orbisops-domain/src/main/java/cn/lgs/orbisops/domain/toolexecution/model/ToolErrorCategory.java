package cn.lgs.orbisops.domain.toolexecution.model;

public enum ToolErrorCategory {
    NONE,
    POLICY_BLOCKED,
    NOT_FOUND,
    INVALID_ARGUMENT,
    AUTHORIZATION,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    REMOTE_PROTOCOL,
    EXECUTION_FAILED,
    RECORDING_FAILED,
    INTERNAL
}
