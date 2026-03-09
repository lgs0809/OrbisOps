package cn.lgs.orbisops.domain.agentdefinition.compilation;

import java.util.List;

public record AgentWorkflowControlFlowFacts(
        String startNodeId,
        List<String> reachableNodeIds,
        List<String> topologicalOrder,
        List<String> terminalNodeIds
) {

    public AgentWorkflowControlFlowFacts {
        startNodeId = required(startNodeId, "WORKFLOW_CONTROL_FLOW_START_REQUIRED");
        reachableNodeIds = reachableNodeIds == null ? List.of() : List.copyOf(reachableNodeIds);
        topologicalOrder = topologicalOrder == null ? List.of() : List.copyOf(topologicalOrder);
        terminalNodeIds = terminalNodeIds == null ? List.of() : List.copyOf(terminalNodeIds);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
