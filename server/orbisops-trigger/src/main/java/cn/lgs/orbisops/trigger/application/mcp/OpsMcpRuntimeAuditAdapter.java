package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpRuntimeAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OpsMcpRuntimeAuditAdapter implements McpRuntimeAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsMcpRuntimeAuditAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) throw new IllegalArgumentException("OPS_CONFIG_AUDIT_SERVICE_REQUIRED");
        this.auditService = auditService;
    }

    @Override
    public void recordRuntimeEvent(String projectId,
                                   String agentId,
                                   String userId,
                                   String module,
                                   String action,
                                   String targetId,
                                   String riskLevel,
                                   String status,
                                   Map<String, Object> payload) {
        auditService.recordRuntimeEvent(projectId, agentId, userId, module, action,
                targetId, riskLevel, status, payload == null ? Map.of() : payload);
    }
}
