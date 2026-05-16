package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentScopeDefinitionPolicy;
import org.springframework.stereotype.Component;

/** Trigger compatibility facade over pure AgentScope definition invariants. */
@Component
public final class OpsAgentScopeDefinitionPolicy {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentScopeDefinitionPolicy domainPolicy;

    public OpsAgentScopeDefinitionPolicy(OpsAgentToolNamePolicy toolNamePolicy) {
        if (toolNamePolicy == null) {
            throw new IllegalArgumentException("AGENT_TOOL_NAME_POLICY_REQUIRED");
        }
        this.mapper = new OpsAgentGraphDefinitionMapper();
        this.domainPolicy = new AgentScopeDefinitionPolicy(toolNamePolicy.domainPolicy());
    }

    public void validate(OpsAgentDefinition definition) {
        domainPolicy.validate(mapper.scopes(definition));
    }
}
