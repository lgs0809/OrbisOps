package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.incident.IncidentAuditEvent;
import cn.lgs.orbisops.application.incident.IncidentAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsIncidentAuditAdapter implements IncidentAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsIncidentAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(IncidentAuditEvent event) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("actor", event.actor());
        after.put("incident", event.after());
        after.put("details", event.details());
        auditService.record("incident", event.action(), event.incidentId(), event.before(), after);
    }
}
