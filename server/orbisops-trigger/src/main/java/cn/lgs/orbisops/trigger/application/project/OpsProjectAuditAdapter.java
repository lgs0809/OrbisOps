package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsProjectAuditAdapter implements ProjectAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsProjectAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String projectId,
                       String module,
                       String action,
                       String targetId,
                       Object before,
                       Object after) {
        auditService.record(projectId, module, action, targetId, before, after);
    }
}
