package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentNodeDefinitionPolicy;
import org.springframework.stereotype.Component;

/** Trigger compatibility facade over pure Agent node domain invariants. */
@Component
public final class OpsAgentNodeDefinitionPolicy {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentNodeDefinitionPolicy domainPolicy;

    public OpsAgentNodeDefinitionPolicy() {
        this(new OpsAgentGraphDefinitionMapper(), new AgentNodeDefinitionPolicy());
    }

    OpsAgentNodeDefinitionPolicy(
            OpsAgentGraphDefinitionMapper mapper,
            AgentNodeDefinitionPolicy domainPolicy) {
        this.mapper = mapper == null ? new OpsAgentGraphDefinitionMapper() : mapper;
        this.domainPolicy = domainPolicy == null
                ? new AgentNodeDefinitionPolicy()
                : domainPolicy;
    }

    public void validate(OpsWorkflowNode node) {
        domainPolicy.validate(mapper.node(node));
    }

    public String normalizedType(OpsWorkflowNode node) {
        return domainPolicy.normalizedType(mapper.node(node));
    }

    AgentNodeDefinitionPolicy domainPolicy() {
        return domainPolicy;
    }
}
