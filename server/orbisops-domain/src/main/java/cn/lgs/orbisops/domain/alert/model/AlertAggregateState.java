package cn.lgs.orbisops.domain.alert.model;

public enum AlertAggregateState {
    FIRING,
    RESOLVED;

    public static AlertAggregateState fromAlertStatus(String status) {
        return "resolved".equalsIgnoreCase(status == null ? "" : status.trim())
                ? RESOLVED
                : FIRING;
    }
}
