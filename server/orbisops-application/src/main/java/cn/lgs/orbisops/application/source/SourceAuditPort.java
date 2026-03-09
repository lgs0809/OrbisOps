package cn.lgs.orbisops.application.source;

public interface SourceAuditPort {
    void record(SourceAuditEvent event);
}
