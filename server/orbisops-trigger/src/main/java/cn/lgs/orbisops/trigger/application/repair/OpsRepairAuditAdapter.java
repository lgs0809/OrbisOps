package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairAuditEvent;
import cn.lgs.orbisops.application.repair.RepairAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsRepairAuditAdapter implements RepairAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsRepairAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(RepairAuditEvent event) {
        if (event == null) return;
        auditService.record(
                event.projectId(), "repair-workspace", event.action(), event.targetId(),
                event.before(), event.after());
    }
}
