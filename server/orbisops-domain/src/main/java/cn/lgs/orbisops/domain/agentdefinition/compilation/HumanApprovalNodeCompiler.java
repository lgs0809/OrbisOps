package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class HumanApprovalNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public HumanApprovalNodeCompiler() {
        super("human-approval-node", Set.of(AgentWorkflowNodeType.HUMAN_APPROVAL));
    }

    @Override
    protected void validate(AgentWorkflowNodeDefinition definition, AgentWorkflowCompilationContext context) {
        if (definition.agent().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_AGENT_REQUIRED:" + definition.nodeId());
        }
    }
}
