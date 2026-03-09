package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionAuditPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

@Component
public class OpsMcpExecutionAuditAdapter implements McpExecutionAuditPort {

    private final OpsConfigAuditService audits;

    public OpsMcpExecutionAuditAdapter(OpsConfigAuditService audits) {
        this.audits = audits;
    }

    @Override
    public void record(McpExecutionAuditEvent event) {
        if (event == null) return;
        audits.record(
                event.projectId(), "mcp-execution", event.action(),
                event.targetId(), null, event.payload());
    }
}
