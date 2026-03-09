package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class RagNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public RagNodeCompiler() {
        super("rag-node", Set.of(AgentWorkflowNodeType.RAG));
    }

    @Override
    protected void validate(AgentWorkflowNodeDefinition definition, AgentWorkflowCompilationContext context) {
        if (definition.agent().isBlank()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_AGENT_REQUIRED:" + definition.nodeId());
        }
    }
}
