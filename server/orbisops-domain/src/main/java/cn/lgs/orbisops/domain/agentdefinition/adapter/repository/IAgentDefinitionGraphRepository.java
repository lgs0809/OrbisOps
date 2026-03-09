package cn.lgs.orbisops.domain.agentdefinition.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;

/** Persistence boundary for the normalized current Agent Definition graph. */
public interface IAgentDefinitionGraphRepository {

    boolean available();

    void replace(AgentDefinitionNormalizedGraphSnapshot snapshot);
}
