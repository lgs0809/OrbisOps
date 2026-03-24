package cn.lgs.orbisops.domain.agenteval.model;

import java.util.List;

public record AgentEvalDefinitionSnapshot(
        String projectId,
        String agentId,
        int version,
        String definitionHash,
        String lifecycle,
        String startNodeId,
        List<AgentEvalNode> nodes,
        List<AgentEvalEdge> edges,
        List<String> agentRoles) {

    public AgentEvalDefinitionSnapshot {
        projectId = required(projectId, "AGENT_EVAL_PROJECT_REQUIRED");
        agentId = required(agentId, "AGENT_EVAL_AGENT_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("AGENT_EVAL_VERSION_INVALID");
        definitionHash = required(definitionHash, "AGENT_EVAL_DEFINITION_HASH_REQUIRED");
        lifecycle = text(lifecycle);
        startNodeId = text(startNodeId);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        agentRoles = agentRoles == null ? List.of() : agentRoles.stream()
                .map(AgentEvalDefinitionSnapshot::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public boolean published() {
        return "PUBLISHED".equalsIgnoreCase(lifecycle);
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
