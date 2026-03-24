package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable registry that expands compiler supported types and rejects ambiguity. */
public final class AgentWorkflowNodeCompilerRegistry {

    private final Map<AgentWorkflowNodeType, AgentWorkflowNodeCompiler> byType;
    private final Map<String, AgentWorkflowNodeCompiler> byId;

    public AgentWorkflowNodeCompilerRegistry(List<AgentWorkflowNodeCompiler> compilers) {
        if (compilers == null || compilers.isEmpty()) {
            throw new IllegalArgumentException("WORKFLOW_NODE_COMPILERS_REQUIRED");
        }
        EnumMap<AgentWorkflowNodeType, AgentWorkflowNodeCompiler> types =
                new EnumMap<>(AgentWorkflowNodeType.class);
        LinkedHashMap<String, AgentWorkflowNodeCompiler> ids = new LinkedHashMap<>();
        for (AgentWorkflowNodeCompiler compiler : compilers) {
            if (compiler == null) continue;
            String id = compiler.compilerId() == null ? "" : compiler.compilerId().trim();
            if (id.isBlank()) throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_ID_REQUIRED");
            if (ids.putIfAbsent(id, compiler) != null) {
                throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_DUPLICATE:" + id);
            }
            Set<AgentWorkflowNodeType> supported = compiler.supportedTypes() == null
                    ? Set.of()
                    : new LinkedHashSet<>(compiler.supportedTypes());
            if (supported.isEmpty()) {
                throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_TYPES_REQUIRED:" + id);
            }
            for (AgentWorkflowNodeType type : supported) {
                if (type == null) throw new IllegalArgumentException("WORKFLOW_NODE_COMPILER_TYPE_REQUIRED:" + id);
                AgentWorkflowNodeCompiler previous = types.putIfAbsent(type, compiler);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "WORKFLOW_NODE_COMPILER_TYPE_DUPLICATE:" + type
                                    + ":" + previous.compilerId() + ":" + id);
                }
            }
        }
        if (types.isEmpty()) throw new IllegalArgumentException("WORKFLOW_NODE_COMPILERS_REQUIRED");
        this.byType = Map.copyOf(types);
        this.byId = Map.copyOf(ids);
    }

    public CompiledWorkflowNode compile(
            AgentWorkflowNodeDefinition definition,
            AgentWorkflowCompilationContext context) {
        if (definition == null) throw new IllegalArgumentException("WORKFLOW_NODE_DEFINITION_REQUIRED");
        AgentWorkflowNodeCompiler compiler = byType.get(definition.nodeType());
        if (compiler == null) {
            throw new IllegalStateException(
                    "WORKFLOW_NODE_COMPILER_NOT_FOUND:" + definition.nodeType());
        }
        return compiler.compile(definition, context);
    }

    public Map<AgentWorkflowNodeType, String> describe() {
        EnumMap<AgentWorkflowNodeType, String> result = new EnumMap<>(AgentWorkflowNodeType.class);
        byType.forEach((type, compiler) -> result.put(type, compiler.compilerId()));
        return Map.copyOf(result);
    }

    public List<String> compilerIds() {
        return List.copyOf(byId.keySet());
    }
}
