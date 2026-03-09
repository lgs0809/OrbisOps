package cn.lgs.orbisops.application.config;

/** Audit boundary for MCP client catalog mutations. */
public interface McpClientAuditPort {

    void created(McpClientDefinition definition);

    void updatedById(Long id, McpClientDefinition before, McpClientDefinition after);

    void updatedByMcpId(String mcpId, McpClientDefinition before, McpClientDefinition after);

    void deletedById(Long id, McpClientDefinition before);

    void deletedByMcpId(String mcpId, McpClientDefinition before);
}
