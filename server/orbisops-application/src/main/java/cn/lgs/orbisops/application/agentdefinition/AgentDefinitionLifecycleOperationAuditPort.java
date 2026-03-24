package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for versioned Agent Definition lifecycle operations. */
public interface AgentDefinitionLifecycleOperationAuditPort<D, S> {

    void recordTransition(
            String action,
            String agentId,
            int version,
            D definition);

    void recordDisable(
            String agentId,
            int version,
            S beforeSnapshot,
            boolean disabled);
}
