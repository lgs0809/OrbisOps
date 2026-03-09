package cn.lgs.orbisops.application.agent;

public enum OpsFailureCategory {
    VALIDATION,
    AUTHORIZATION,
    POLICY,
    DEPENDENCY,
    TIMEOUT,
    CONFLICT,
    BOUNDARY,
    UNKNOWN
}
