package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionSnapshot;

import java.util.Optional;

/** Application boundary for encoding, decoding, copying and hashing Agent Definition snapshots. */
public interface AgentDefinitionSnapshotMapper<T> {

    Optional<T> fromSnapshot(AgentDefinitionSnapshot snapshot);

    AgentDefinitionSnapshot toSnapshot(T definition, boolean currentPublished);

    T copy(T definition);

    String definitionHash(T definition);
}
