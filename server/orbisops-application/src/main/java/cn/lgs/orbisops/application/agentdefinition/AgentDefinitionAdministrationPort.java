package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Outbound persistence boundary for destructive and reload Agent Definition administration. */
public interface AgentDefinitionAdministrationPort<D, S> {

    S currentSnapshot(String agentId);

    boolean delete(String agentId);

    void reload();

    List<D> listProjectDefinitions();
}
