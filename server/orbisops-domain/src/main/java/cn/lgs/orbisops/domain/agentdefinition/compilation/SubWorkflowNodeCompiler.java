package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class SubWorkflowNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public SubWorkflowNodeCompiler() {
        super("sub-workflow-node", Set.of(AgentWorkflowNodeType.SUB_WORKFLOW));
    }

    @Override
    protected void validate(AgentWorkflowNodeDefinition definition, AgentWorkflowCompilationContext context) {
        if (definition.agent().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_AGENT_REQUIRED:" + definition.nodeId());
        }
    }
}
