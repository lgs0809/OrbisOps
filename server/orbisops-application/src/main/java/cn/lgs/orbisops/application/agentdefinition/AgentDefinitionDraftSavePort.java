package cn.lgs.orbisops.application.agentdefinition;

/** Outbound mutable-definition boundary required before saving a new Agent Definition draft. */
public interface AgentDefinitionDraftSavePort<D> {

    void assertProjectAndBindingsValid(D definition);

    D normalizeExecutionShape(D definition);
}
