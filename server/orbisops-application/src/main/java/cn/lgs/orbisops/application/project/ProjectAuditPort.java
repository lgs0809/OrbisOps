package cn.lgs.orbisops.application.project;

public interface ProjectAuditPort {
    void record(String projectId, String module, String action, String targetId, Object before, Object after);
}
