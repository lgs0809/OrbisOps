package cn.lgs.orbisops.domain.agentdefinition.adapter.repository;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for current and immutable Agent Definition versions. */
public interface IAgentDefinitionRepository {

    boolean available();

    List<AgentDefinitionSnapshot> listCurrentEnabled();

    List<AgentDefinitionSnapshot> listVersionsEnabled();

    Optional<AgentDefinitionSnapshot> findVersion(String agentId, int version);

    int maxVersion(String agentId);

    void saveVersion(AgentDefinitionSnapshot snapshot);

    AgentDefinitionPublishResult publish(AgentDefinitionSnapshot snapshot, String expectedVersionHash);

    boolean disableVersion(String agentId, int version);

    void disableCurrent(String agentId);
}
