package cn.lgs.orbisops.application.modelpolicy;

public interface ModelPolicyAuditPort {
    void record(String projectId, Object before, Object after);
}
