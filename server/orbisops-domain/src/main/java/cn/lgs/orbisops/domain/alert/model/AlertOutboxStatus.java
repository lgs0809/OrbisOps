package cn.lgs.orbisops.domain.alert.model;

public enum AlertOutboxStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    DEAD_LETTER
}
