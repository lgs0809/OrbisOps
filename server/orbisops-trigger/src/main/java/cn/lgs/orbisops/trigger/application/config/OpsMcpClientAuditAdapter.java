package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.McpClientAuditPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.Map;

/** Operations audit adapter for MCP client catalog mutations. */
public final class OpsMcpClientAuditAdapter implements McpClientAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsMcpClientAuditAdapter(OpsConfigAuditService auditService) {
        if (auditService == null) {
            throw new IllegalArgumentException("MCP_CLIENT_AUDIT_SERVICE_REQUIRED");
        }
        this.auditService = auditService;
    }

    @Override
    public void created(McpClientDefinition definition) {
        auditService.record("mcp-config", "create", definition.mcpId(), null, definition);
    }

    @Override
    public void updatedById(Long id, McpClientDefinition before, McpClientDefinition after) {
        auditService.record("mcp-config", "update-by-id", String.valueOf(id), before, after);
    }

    @Override
    public void updatedByMcpId(String mcpId, McpClientDefinition before, McpClientDefinition after) {
        auditService.record("mcp-config", "update-by-mcp-id", mcpId, before, after);
    }

    @Override
    public void deletedById(Long id, McpClientDefinition before) {
        auditService.record("mcp-config", "delete-by-id", String.valueOf(id), before, Map.of("deleted", true));
    }

    @Override
    public void deletedByMcpId(String mcpId, McpClientDefinition before) {
        auditService.record("mcp-config", "delete-by-mcp-id", mcpId, before, Map.of("deleted", true));
    }
}
