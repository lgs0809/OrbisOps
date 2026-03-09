package cn.lgs.orbisops.application.alert;

public interface AlertAuditPort {
    void record(AlertRuleAuditEvent event);
}
