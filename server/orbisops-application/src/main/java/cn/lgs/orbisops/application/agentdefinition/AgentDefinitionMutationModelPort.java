package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionLifecycle;

/** Adapter boundary for mutating an implementation-specific Agent Definition model. */
public interface AgentDefinitionMutationModelPort<D> {

    String agentId(D definition);

    Integer version(D definition);

    String definitionHash(D definition);

    boolean hasLifecycle(D definition);

    void assignVersion(D definition, int version);

    void assignLifecycle(D definition, AgentDefinitionLifecycle lifecycle);

    void assignSource(D definition, String source);

    void assignDefinitionHash(D definition, String definitionHash);

    void normalize(D definition);

    void validate(D definition);

    D snapshot(D definition);

    String calculateHash(D definition);
}
