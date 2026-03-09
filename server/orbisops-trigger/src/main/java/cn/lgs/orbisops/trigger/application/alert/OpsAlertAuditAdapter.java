package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.application.alert.AlertAuditPort;
import cn.lgs.orbisops.application.alert.AlertRuleAuditEvent;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsAlertAuditAdapter implements AlertAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsAlertAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(AlertRuleAuditEvent event) {
        if (event == null) throw new IllegalArgumentException("ALERT_RULE_AUDIT_EVENT_REQUIRED");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("result", event.after());
        after.put("actor", event.actor());
        auditService.record(
                "alert-trigger",
                event.action(),
                event.targetId(),
                event.before(),
                after);
    }
}
