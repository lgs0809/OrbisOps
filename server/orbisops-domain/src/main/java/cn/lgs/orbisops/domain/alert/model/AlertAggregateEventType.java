package cn.lgs.orbisops.domain.alert.model;

public enum AlertAggregateEventType {
    FIRST(true),
    RECURRENCE(true),
    ESCALATION(true),
    RECOVERY(true),
    DUPLICATE(false),
    IGNORED_RECOVERY(false),
    SUMMARY(true);

    private final boolean dispatchNow;

    AlertAggregateEventType(boolean dispatchNow) {
        this.dispatchNow = dispatchNow;
    }

    public boolean dispatchNow() {
        return dispatchNow;
    }
}
