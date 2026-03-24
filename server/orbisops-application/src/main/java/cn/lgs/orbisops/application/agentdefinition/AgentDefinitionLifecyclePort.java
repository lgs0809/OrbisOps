package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionVersionState;

public interface AgentDefinitionLifecyclePort<D> {

    D saveDraft(D definition);

    D definition(String agentId, int version);

    AgentDefinitionVersionState state(String agentId, int version);

    D transition(AgentDefinitionVersionState expected, AgentDefinitionVersionState target);

    D republish(AgentDefinitionVersionState expected);

    boolean disable(AgentDefinitionVersionState expected);
}
