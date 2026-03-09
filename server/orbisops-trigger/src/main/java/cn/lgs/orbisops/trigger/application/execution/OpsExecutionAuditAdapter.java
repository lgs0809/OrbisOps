package cn.lgs.orbisops.trigger.application.execution;

import cn.lgs.orbisops.application.execution.ExecutionAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsExecutionAuditAdapter implements ExecutionAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsExecutionAuditAdapter(OpsConfigAuditService auditService) {
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
