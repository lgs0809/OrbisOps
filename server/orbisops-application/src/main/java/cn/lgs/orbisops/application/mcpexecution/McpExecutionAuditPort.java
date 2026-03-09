package cn.lgs.orbisops.application.mcpexecution;

public interface McpExecutionAuditPort {

    void record(McpExecutionAuditEvent event);

    record McpExecutionAuditEvent(
            String projectId,
            String action,
            String targetId,
            Object payload) {
    }
}
