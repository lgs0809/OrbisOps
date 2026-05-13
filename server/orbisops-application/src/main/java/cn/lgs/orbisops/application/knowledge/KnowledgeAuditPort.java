package cn.lgs.orbisops.application.knowledge;

public interface KnowledgeAuditPort {
    void record(String projectId, String action, String kbId, Object before, Object after);
}
