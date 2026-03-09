package cn.lgs.orbisops.application.toolset;

public interface ToolsetAuditPort {
    void record(String projectId, String action, String targetId, Object before, Object after);
}
