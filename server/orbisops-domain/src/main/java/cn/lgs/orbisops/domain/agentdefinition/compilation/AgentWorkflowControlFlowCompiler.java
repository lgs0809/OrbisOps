package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;

import java.util.List;

@FunctionalInterface
public interface AgentWorkflowControlFlowCompiler {

    AgentWorkflowControlFlowFacts compile(
            AgentWorkflowDefinition definition,
            List<CompiledWorkflowNode> nodes,
            List<CompiledWorkflowEdge> edges);
}
