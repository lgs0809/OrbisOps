package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for capability binding update completion. */
public interface AgentDefinitionBindingUpdateAuditPort<D, V> {

    void recordBindingUpdate(
            String agentId,
            D savedDefinition,
            V effectiveBindings);
}
