package cn.lgs.orbisops.application.evidence;

public interface EvidenceAuditPort {

    void record(EvidenceAuditEvent event);

    record EvidenceAuditEvent(
            String projectId,
            String module,
            String action,
            String targetId,
            Object payload) {
    }
}
