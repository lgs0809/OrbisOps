package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpsControlledCodeAuditAdapter implements ControlledCodeAuditPort {

    private final OpsConfigAuditService audits;

    public OpsControlledCodeAuditAdapter(ObjectProvider<OpsConfigAuditService> provider) {
        this.audits = provider.getIfAvailable();
    }

    @Override
    public void record(ControlledCodeAuditEvent event) {
        if (audits == null || event == null) return;
        audits.recordRuntimeEvent(
                event.projectId(), "", event.actor(), "controlled-code-tool",
                event.action().auditName(), event.targetId(), event.riskLevel(),
                event.status(), event.payload());
    }
}
