package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsMcpAuditAdapter implements McpAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsMcpAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String projectId,
                       String module,
                       String action,
                       String targetId,
                       Object before,
                       Object after) {
        if (projectId == null || projectId.isBlank()) {
            auditService.record(module, action, targetId, before, after);
            return;
        }
        auditService.record(projectId, module, action, targetId, before, after);
    }
}
