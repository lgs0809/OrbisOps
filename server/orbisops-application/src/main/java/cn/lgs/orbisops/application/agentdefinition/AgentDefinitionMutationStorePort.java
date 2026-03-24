package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionPublishResult;

import java.util.Optional;

/** Persistence and projection boundary used by Agent Definition mutation use cases. */
public interface AgentDefinitionMutationStorePort<D> {

    boolean available();

    int maxVersion(String agentId);

    Optional<D> findVersion(String agentId, int version);

    void saveVersion(D definition, boolean currentPublished);

    AgentDefinitionPublishResult publish(D definition, String expectedVersionHash);

    boolean disableVersion(String agentId, int version);

    void disableCurrent(String agentId);
}
