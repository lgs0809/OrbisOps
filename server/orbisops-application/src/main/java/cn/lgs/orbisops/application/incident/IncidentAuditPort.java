package cn.lgs.orbisops.application.incident;

public interface IncidentAuditPort {
    void record(IncidentAuditEvent event);
}
