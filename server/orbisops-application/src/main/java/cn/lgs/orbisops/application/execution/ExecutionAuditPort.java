package cn.lgs.orbisops.application.execution;

public interface ExecutionAuditPort {
    void record(String projectId, String module, String action, String targetId, Object before, Object after);
}
