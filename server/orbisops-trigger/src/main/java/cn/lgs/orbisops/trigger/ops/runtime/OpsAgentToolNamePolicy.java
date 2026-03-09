package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentToolNamePolicy;
import org.springframework.stereotype.Component;

import java.util.List;

/** Trigger compatibility facade over pure model-visible tool-name invariants. */
@Component
public final class OpsAgentToolNamePolicy {

    private final AgentToolNamePolicy domainPolicy;

    public OpsAgentToolNamePolicy() {
        this(new AgentToolNamePolicy());
    }

    OpsAgentToolNamePolicy(AgentToolNamePolicy domainPolicy) {
        this.domainPolicy = domainPolicy == null
                ? new AgentToolNamePolicy()
                : domainPolicy;
    }

    public void validateAll(List<String> toolNames, String owner) {
        domainPolicy.validateAll(toolNames, owner);
    }

    public void validate(String toolName, String owner) {
        domainPolicy.validate(toolName, owner);
    }

    AgentToolNamePolicy domainPolicy() {
        return domainPolicy;
    }
}
