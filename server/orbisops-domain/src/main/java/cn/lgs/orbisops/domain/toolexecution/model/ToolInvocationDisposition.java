package cn.lgs.orbisops.domain.toolexecution.model;

/** Stable caller-facing disposition independent of invocation protocol. */
public enum ToolInvocationDisposition {
    SUCCEEDED,
    BLOCKED,
    FAILED,
    UNKNOWN
}
