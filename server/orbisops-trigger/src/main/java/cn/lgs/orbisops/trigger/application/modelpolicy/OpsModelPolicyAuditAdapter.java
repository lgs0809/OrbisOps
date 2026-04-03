package cn.lgs.orbisops.trigger.application.modelpolicy;

import cn.lgs.orbisops.application.modelpolicy.ModelPolicyAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsModelPolicyAuditAdapter implements ModelPolicyAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsModelPolicyAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String projectId, Object before, Object after) {
        auditService.record(projectId, "model-default-policy", "update",
                projectId == null || projectId.isBlank() ? "GLOBAL" : projectId, before, after);
    }
}
