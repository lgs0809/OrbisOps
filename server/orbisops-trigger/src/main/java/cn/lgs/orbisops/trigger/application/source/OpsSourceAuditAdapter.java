package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.source.SourceAuditEvent;
import cn.lgs.orbisops.application.source.SourceAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsSourceAuditAdapter implements SourceAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsSourceAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(SourceAuditEvent event) {
        if (event == null) return;
        if (event.projectId().isBlank()) {
            auditService.record(event.module(), event.action(), event.targetId(), event.before(), event.after());
            return;
        }
        auditService.record(
                event.projectId(), event.module(), event.action(), event.targetId(), event.before(), event.after());
    }
}
