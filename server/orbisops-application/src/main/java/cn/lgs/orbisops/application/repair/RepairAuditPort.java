package cn.lgs.orbisops.application.repair;

public interface RepairAuditPort {
    void record(RepairAuditEvent event);
}
