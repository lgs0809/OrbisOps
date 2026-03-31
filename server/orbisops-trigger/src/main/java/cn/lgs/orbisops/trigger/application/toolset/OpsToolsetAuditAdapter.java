package cn.lgs.orbisops.trigger.application.toolset;

import cn.lgs.orbisops.application.toolset.ToolsetAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsToolsetAuditAdapter implements ToolsetAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsToolsetAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String projectId, String action, String targetId, Object before, Object after) {
        auditService.record(projectId, "toolset", action, targetId, before, after);
    }
}
