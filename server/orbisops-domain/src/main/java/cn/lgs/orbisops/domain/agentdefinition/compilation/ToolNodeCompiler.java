package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class ToolNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public ToolNodeCompiler() {
        super("tool-node", Set.of(AgentWorkflowNodeType.TOOL));
    }

    @Override
    protected void validate(AgentWorkflowNodeDefinition definition, AgentWorkflowCompilationContext context) {
        if (definition.agent().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_AGENT_REQUIRED:" + definition.nodeId());
        }
    }
}
