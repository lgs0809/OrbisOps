package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public interface AgentWorkflowNodeCompiler {

    String compilerId();

    Set<AgentWorkflowNodeType> supportedTypes();

    CompiledWorkflowNode compile(
            AgentWorkflowNodeDefinition definition,
            AgentWorkflowCompilationContext context);
}
