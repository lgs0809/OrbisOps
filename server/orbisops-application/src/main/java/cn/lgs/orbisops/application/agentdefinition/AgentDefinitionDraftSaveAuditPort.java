package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for Agent Definition draft persistence. */
public interface AgentDefinitionDraftSaveAuditPort<D> {

    void recordDraftSave(String auditAction, D savedDefinition);
}
