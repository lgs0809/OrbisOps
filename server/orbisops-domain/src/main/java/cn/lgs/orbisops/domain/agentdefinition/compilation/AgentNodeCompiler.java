package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class AgentNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public AgentNodeCompiler() {
        super("agent-node", Set.of(AgentWorkflowNodeType.AGENT));
    }

    @Override
    protected void validate(AgentWorkflowNodeDefinition definition, AgentWorkflowCompilationContext context) {
        if (definition.agent().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_AGENT_REQUIRED:" + definition.nodeId());
        }
    }
}
