package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class BoundaryNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public BoundaryNodeCompiler() {
        super("boundary-node", Set.of(
                AgentWorkflowNodeType.START,
                AgentWorkflowNodeType.END));
    }
}
