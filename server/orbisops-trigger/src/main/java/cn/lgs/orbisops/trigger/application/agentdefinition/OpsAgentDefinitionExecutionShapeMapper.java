package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionNodeFact;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentExecutionShapeDecision;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentExecutionShapePolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;

import java.util.List;

/** Applies the pure Agent execution-shape decision to mutable inbound definition DTOs. */
public final class OpsAgentDefinitionExecutionShapeMapper {

    private final AgentExecutionShapePolicy domainPolicy =
            new AgentExecutionShapePolicy();

    public OpsAgentDefinition normalize(OpsAgentDefinition definition) {
        if (definition == null) return null;
        AgentExecutionShapeDecision decision = domainPolicy.decide(
                nodes(definition.getNodes()));
        definition.setEngine(decision.engine());
        if (decision.clearLegacyAgentScope()) {
            definition.setAgentScopeMode(null);
            definition.setAgentScopeMaxConcurrency(null);
            definition.setAgentscopeAgents(List.of());
        }
        return definition;
    }

    public String inferEngine(OpsAgentDefinition definition) {
        return domainPolicy.decide(definition == null
                ? List.of()
                : nodes(definition.getNodes())).engine();
    }

    private List<AgentExecutionNodeFact> nodes(List<OpsWorkflowNode> nodes) {
        return nodes == null
                ? List.of()
                : nodes.stream()
                .filter(java.util.Objects::nonNull)
                .map(node -> new AgentExecutionNodeFact(
                        node.getType(),
                        node.getMode(),
                        node.getSubEngine()))
                .toList();
    }
}
