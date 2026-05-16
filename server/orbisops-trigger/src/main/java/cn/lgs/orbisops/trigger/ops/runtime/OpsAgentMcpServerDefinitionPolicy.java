package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentMcpServerDefinitionPolicy;
import org.springframework.stereotype.Component;

import java.util.List;

/** Trigger compatibility facade over pure inline MCP definition invariants. */
@Component
public final class OpsAgentMcpServerDefinitionPolicy {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentMcpServerDefinitionPolicy domainPolicy;

    public OpsAgentMcpServerDefinitionPolicy(OpsAgentToolNamePolicy toolNamePolicy) {
        if (toolNamePolicy == null) {
            throw new IllegalArgumentException("AGENT_TOOL_NAME_POLICY_REQUIRED");
        }
        this.mapper = new OpsAgentGraphDefinitionMapper();
        this.domainPolicy = new AgentMcpServerDefinitionPolicy(toolNamePolicy.domainPolicy());
    }

    public void validate(List<OpsMcpServerConfig> servers, String owner) {
        domainPolicy.validate(mapper.mcpServers(servers), owner);
    }

    AgentMcpServerDefinitionPolicy domainPolicy() {
        return domainPolicy;
    }
}
