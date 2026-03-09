package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Read port for immutable Agent Definition snapshots. */
public interface AgentDefinitionCatalogPort<D> {

    String defaultAgentId();

    D findCurrent(String agentId);

    D findVersion(String agentId, int version);

    List<D> findCurrentDefinitions();

    List<D> findVersions(String agentId);
}
