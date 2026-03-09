package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Coordinates Agent graph header, node and topology invariants. */
public final class AgentGraphDefinitionPolicy {

    private static final Set<String> ENGINES = Set.of(
            "CHAT", "GRAPH", "AGENTSCOPE", "HYBRID",
            "SPRING_AI_ALIBABA_STATE_GRAPH", "CONTROLLED_GRAPH");

    private final AgentNodeDefinitionPolicy nodePolicy;
    private final AgentGraphTopologyPolicy topologyPolicy;

    public AgentGraphDefinitionPolicy() {
        this(new AgentNodeDefinitionPolicy());
    }

    public AgentGraphDefinitionPolicy(AgentNodeDefinitionPolicy nodePolicy) {
        this(nodePolicy, new AgentGraphTopologyPolicy(nodePolicy));
    }

    public AgentGraphDefinitionPolicy(
            AgentNodeDefinitionPolicy nodePolicy,
            AgentGraphTopologyPolicy topologyPolicy) {
        if (nodePolicy == null) {
            throw new IllegalArgumentException("AGENT_NODE_DEFINITION_POLICY_REQUIRED");
        }
        if (topologyPolicy == null) {
            throw new IllegalArgumentException("AGENT_GRAPH_TOPOLOGY_POLICY_REQUIRED");
        }
        this.nodePolicy = nodePolicy;
        this.topologyPolicy = topologyPolicy;
    }

    public void validate(AgentGraphDefinition definition) {
        validateHeader(definition);
        List<AgentGraphDefinition.Node> nodes = definition.nodes();
        if (topologyPolicy.requiresGraph(definition.engine()) && nodes.isEmpty()) {
            throw new IllegalArgumentException("Graph/HYBRID Agent 定义缺少 nodes");
        }
        Set<String> nodeIds = new HashSet<>();
        Map<String, AgentGraphDefinition.Node> nodeById = new HashMap<>();
        for (AgentGraphDefinition.Node node : nodes) {
            nodePolicy.validate(node);
            if (!nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException("节点 ID 重复：" + node.nodeId());
            }
            nodeById.put(node.nodeId(), node);
        }
        topologyPolicy.validate(definition, nodes, nodeIds, nodeById);
    }

    private void validateHeader(AgentGraphDefinition definition) {
        if (definition == null || !hasText(definition.agentId())) {
            throw new IllegalArgumentException("Agent 定义缺少 agentId");
        }
        String engine = normalizeEngine(definition.engine(), "GRAPH");
        if (!ENGINES.contains(engine)) {
            throw new IllegalArgumentException(
                    "不支持的 Agent engine：" + definition.engine());
        }
    }

    private String normalizeEngine(String value, String fallback) {
        if (!hasText(value)) return fallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.contains("AGENTSCOPE")) return "AGENTSCOPE";
        if (normalized.contains("HYBRID")) return "HYBRID";
        if (normalized.contains("CHAT")) return "CHAT";
        if (normalized.contains("GRAPH")) return "GRAPH";
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
