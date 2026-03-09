package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentGraphTopologyPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trigger compatibility facade over pure Agent graph-topology domain invariants. */
@Component
public final class OpsAgentGraphTopologyPolicy {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentGraphTopologyPolicy domainPolicy;

    @Autowired
    public OpsAgentGraphTopologyPolicy(OpsAgentNodeDefinitionPolicy nodePolicy) {
        this(
                new OpsAgentGraphDefinitionMapper(),
                new AgentGraphTopologyPolicy(
                        nodePolicy == null
                                ? new cn.lgs.orbisops.domain.agentdefinition.service.AgentNodeDefinitionPolicy()
                                : nodePolicy.domainPolicy()));
    }

    OpsAgentGraphTopologyPolicy(
            OpsAgentGraphDefinitionMapper mapper,
            AgentGraphTopologyPolicy domainPolicy) {
        this.mapper = mapper == null ? new OpsAgentGraphDefinitionMapper() : mapper;
        this.domainPolicy = domainPolicy == null
                ? new AgentGraphTopologyPolicy(
                new cn.lgs.orbisops.domain.agentdefinition.service.AgentNodeDefinitionPolicy())
                : domainPolicy;
    }

    public void validate(
            OpsAgentDefinition definition,
            List<OpsWorkflowNode> nodes,
            Set<String> nodeIds,
            Map<String, OpsWorkflowNode> nodeById) {
        domainPolicy.validate(mapper.map(definition));
    }

    public boolean requiresGraph(String engine) {
        return domainPolicy.requiresGraph(engine);
    }

    AgentGraphTopologyPolicy domainPolicy() {
        return domainPolicy;
    }
}
