package cn.lgs.orbisops.application.mcp;

public interface McpAuditPort {
    void record(String projectId, String module, String action, String targetId, Object before, Object after);
}
