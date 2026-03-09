package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentGraphDefinitionPolicy;
import org.springframework.stereotype.Component;

/** Trigger compatibility facade over pure Agent graph-definition domain invariants. */
@Component
public final class OpsAgentGraphDefinitionPolicy {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentGraphDefinitionPolicy domainPolicy;

    public OpsAgentGraphDefinitionPolicy(
            OpsAgentNodeDefinitionPolicy nodePolicy,
            OpsAgentGraphTopologyPolicy topologyPolicy) {
        if (nodePolicy == null) {
            throw new IllegalArgumentException("AGENT_NODE_DEFINITION_POLICY_REQUIRED");
        }
        if (topologyPolicy == null) {
            throw new IllegalArgumentException("AGENT_GRAPH_TOPOLOGY_POLICY_REQUIRED");
        }
        this.mapper = new OpsAgentGraphDefinitionMapper();
        this.domainPolicy = new AgentGraphDefinitionPolicy(
                nodePolicy.domainPolicy(),
                topologyPolicy.domainPolicy());
    }

    public void validate(OpsAgentDefinition definition) {
        domainPolicy.validate(mapper.map(definition));
    }

    AgentGraphDefinitionPolicy domainPolicy() {
        return domainPolicy;
    }
}
