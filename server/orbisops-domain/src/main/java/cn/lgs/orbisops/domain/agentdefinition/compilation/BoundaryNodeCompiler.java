package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class BoundaryNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public BoundaryNodeCompiler() {
        super("boundary-node", Set.of(
                AgentWorkflowNodeType.START,
                AgentWorkflowNodeType.END));
    }

    @Override
    protected void validate(cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition definition,
                            AgentWorkflowCompilationContext context) {
        if (definition.nodeType() == AgentWorkflowNodeType.START) {
            new cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowToolCallBudgetPolicy().configuredLimit(definition.config());
        }
    }
}
