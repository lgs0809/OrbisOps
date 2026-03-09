package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

abstract class AbstractAgentWorkflowNodeCompiler implements AgentWorkflowNodeCompiler {

    private final String compilerId;
    private final Set<AgentWorkflowNodeType> supportedTypes;

    protected AbstractAgentWorkflowNodeCompiler(
            String compilerId,
            Set<AgentWorkflowNodeType> supportedTypes) {
        this.compilerId = required(compilerId, "WORKFLOW_NODE_COMPILER_ID_REQUIRED");
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_TYPES_REQUIRED:" + compilerId);
        }
        this.supportedTypes = Set.copyOf(supportedTypes);
    }

    @Override
    public final String compilerId() {
        return compilerId;
    }

    @Override
    public final Set<AgentWorkflowNodeType> supportedTypes() {
        return supportedTypes;
    }

    @Override
    public CompiledWorkflowNode compile(
            AgentWorkflowNodeDefinition definition,
            AgentWorkflowCompilationContext context) {
        if (definition == null) throw new IllegalArgumentException("WORKFLOW_NODE_DEFINITION_REQUIRED");
        if (!supportedTypes.contains(definition.nodeType())) {
            throw new IllegalArgumentException(
                    "WORKFLOW_NODE_COMPILER_TYPE_UNSUPPORTED:" + definition.nodeType());
        }
        validate(definition, context);
        return new CompiledWorkflowNode(
                definition.nodeId(),
                definition.nodeType(),
                definition.publishedType(),
                compilerId,
                definition.resources(),
                definition.config());
    }

    protected void validate(
            AgentWorkflowNodeDefinition definition,
            AgentWorkflowCompilationContext context) {
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
