package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

/** Explicit runtime enablement boundary for the full Workflow node language. */
public final class AgentWorkflowNodeTypePolicy {

    private static final Set<AgentWorkflowNodeType> ENABLED_TYPES = Set.of(
            AgentWorkflowNodeType.START,
            AgentWorkflowNodeType.END,
            AgentWorkflowNodeType.LLM,
            AgentWorkflowNodeType.AGENT,
            AgentWorkflowNodeType.TOOL,
            AgentWorkflowNodeType.RAG,
            AgentWorkflowNodeType.CONDITION,
            AgentWorkflowNodeType.HUMAN_APPROVAL,
            AgentWorkflowNodeType.SUB_WORKFLOW);

    public void validateEnabled(AgentWorkflowNodeDefinition node) {
        if (node == null) throw new IllegalArgumentException("WORKFLOW_NODE_DEFINITION_REQUIRED");
        if (!ENABLED_TYPES.contains(node.nodeType())) {
            throw new IllegalArgumentException(
                    "WORKFLOW_NODE_TYPE_NOT_ENABLED:" + node.nodeType());
        }
    }

    public Set<AgentWorkflowNodeType> enabledTypes() {
        return ENABLED_TYPES;
    }
}
