package cn.lgs.orbisops.application.memory;

@FunctionalInterface
public interface ContextMemoryAuditPort {

    void record(ContextMemoryAuditEvent event);
}
