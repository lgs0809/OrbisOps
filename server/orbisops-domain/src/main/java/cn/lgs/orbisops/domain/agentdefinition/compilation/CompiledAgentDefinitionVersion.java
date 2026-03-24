package cn.lgs.orbisops.domain.agentdefinition.compilation;

import java.util.List;

/** Structural compilation output. Runtime resources are intentionally not bound yet. */
public record CompiledAgentDefinitionVersion(
        int schemaVersion,
        int definitionVersion,
        String definitionHash,
        String agentId,
        String startNodeId,
        List<CompiledWorkflowNode> nodes,
        List<CompiledWorkflowEdge> edges,
        List<String> reachableNodeIds,
        List<String> topologicalOrder,
        List<String> terminalNodeIds,
        List<String> completedStages
) {

    public CompiledAgentDefinitionVersion {
        if (schemaVersion <= 0) throw new IllegalArgumentException("COMPILED_SCHEMA_VERSION_INVALID");
        if (definitionVersion < 0) throw new IllegalArgumentException("COMPILED_DEFINITION_VERSION_INVALID");
        definitionHash = definitionHash == null ? "" : definitionHash.trim();
        agentId = required(agentId, "COMPILED_AGENT_ID_REQUIRED");
        startNodeId = required(startNodeId, "COMPILED_START_NODE_REQUIRED");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        reachableNodeIds = reachableNodeIds == null ? List.of() : List.copyOf(reachableNodeIds);
        topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
        terminalNodeIds = terminalNodeIds == null ? List.of() : List.copyOf(terminalNodeIds);
        completedStages = completedStages == null ? List.of() : List.copyOf(completedStages);
        if (nodes.isEmpty()) throw new IllegalArgumentException("COMPILED_WORKFLOW_NODES_REQUIRED");
        if (reachableNodeIds.size() != nodes.size()) {
            throw new IllegalArgumentException("COMPILED_REACHABILITY_INCOMPLETE");
        }
    }

    public boolean versioned() {
        return definitionVersion > 0 && !definitionHash.isBlank();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
