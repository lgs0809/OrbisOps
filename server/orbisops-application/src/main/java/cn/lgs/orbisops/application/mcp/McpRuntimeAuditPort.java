package cn.lgs.orbisops.application.mcp;

import java.util.Map;

public interface McpRuntimeAuditPort {

    void recordRuntimeEvent(String projectId,
                            String agentId,
                            String userId,
                            String module,
                            String action,
                            String targetId,
                            String riskLevel,
                            String status,
                            Map<String, Object> payload);
}
