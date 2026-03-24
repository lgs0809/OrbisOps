package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;

/** Maps an adapter-specific definition payload to its domain identity and snapshot. */
public interface AgentDefinitionDescriptorPort<D> {

    AgentDefinitionVersionState describe(D definition);

    D snapshot(D definition);
}
