package cn.lgs.orbisops.application.agentdefinition;

/** Outbound audit boundary for destructive and reload Agent Definition administration. */
public interface AgentDefinitionAdministrationAuditPort<S> {

    void recordDelete(String agentId, S beforeSnapshot, boolean deleted);

    void recordReload();
}
