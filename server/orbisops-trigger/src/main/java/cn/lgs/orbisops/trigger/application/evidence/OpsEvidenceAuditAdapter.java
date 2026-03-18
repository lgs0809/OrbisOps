package cn.lgs.orbisops.trigger.application.evidence;

import cn.lgs.orbisops.application.evidence.EvidenceAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpsEvidenceAuditAdapter implements EvidenceAuditPort {

    private final OpsConfigAuditService audits;

    public OpsEvidenceAuditAdapter(ObjectProvider<OpsConfigAuditService> provider) {
        this.audits = provider.getIfAvailable();
    }

    @Override
    public void record(EvidenceAuditEvent event) {
        if (audits == null || event == null) return;
        audits.record(
                event.projectId(), event.module(), event.action(), event.targetId(), null, event.payload());
    }
}
