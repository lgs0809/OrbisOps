package cn.lgs.orbisops.domain.alert.model;

public enum AlertRuleStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    AlertRuleStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static AlertRuleStatus require(Integer value) {
        if (value == null) return ENABLED;
        for (AlertRuleStatus status : values()) {
            if (status.code == value) return status;
        }
        throw new IllegalArgumentException("ALERT_RULE_STATUS_UNKNOWN:" + value);
    }
}
