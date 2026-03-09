package cn.lgs.orbisops.domain.alert.model;

public enum AlertOutboxQuotaDecision {
    ACCEPT,
    PREEMPT_LOWER_PRIORITY,
    REJECT
}
