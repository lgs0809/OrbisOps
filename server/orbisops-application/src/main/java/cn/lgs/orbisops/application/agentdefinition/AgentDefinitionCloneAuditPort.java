package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for Agent Definition clone completion. */
public interface AgentDefinitionCloneAuditPort<D> {

    void recordClone(
            String sourceAgentId,
            String targetAgentId,
            D clonedDefinition);
}
