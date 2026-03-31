package cn.lgs.orbisops.application.toolexecution;

public interface ToolExecutionAuditPort {

    void record(ToolExecutionAuditEvent event);

    record ToolExecutionAuditEvent(
            String projectId,
            String action,
            String targetId,
            Object payload) {
    }
}
