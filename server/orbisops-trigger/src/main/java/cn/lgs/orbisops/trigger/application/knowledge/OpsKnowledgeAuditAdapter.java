package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsKnowledgeAuditAdapter implements KnowledgeAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsKnowledgeAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String projectId, String action, String kbId, Object before, Object after) {
        if (projectId == null || projectId.isBlank()) {
            auditService.record("knowledge-base", action, kbId, before, after);
            return;
        }
        auditService.record(projectId, "knowledge-base", action, kbId, before, after);
    }
}
