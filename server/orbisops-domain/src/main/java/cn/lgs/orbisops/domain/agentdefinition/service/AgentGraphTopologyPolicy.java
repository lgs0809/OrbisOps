package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Domain invariants for graph edges, reachability, convergence and declared loops. */
public final class AgentGraphTopologyPolicy {

    private final AgentNodeDefinitionPolicy nodePolicy;

    public AgentGraphTopologyPolicy(AgentNodeDefinitionPolicy nodePolicy) {
        if (nodePolicy == null) {
            throw new IllegalArgumentException("AGENT_NODE_DEFINITION_POLICY_REQUIRED");
        }
        this.nodePolicy = nodePolicy;
    }

    public void validate(AgentGraphDefinition definition) {
        AgentGraphDefinition safeDefinition = definition == null
                ? new AgentGraphDefinition("", "", "", null, null, null)
                : definition;
        List<AgentGraphDefinition.Node> nodes = safeDefinition.nodes();
        Set<String> nodeIds = new HashSet<>();
        Map<String, AgentGraphDefinition.Node> nodeById = new HashMap<>();
        for (AgentGraphDefinition.Node node : nodes) {
            if (node != null && hasText(node.nodeId())) {
                nodeIds.add(node.nodeId());
                nodeById.put(node.nodeId(), node);
            }
        }
        validate(safeDefinition, nodes, nodeIds, nodeById);
    }

    public void validate(
            AgentGraphDefinition definition,
            List<AgentGraphDefinition.Node> nodes,
            Set<String> nodeIds,
            Map<String, AgentGraphDefinition.Node> nodeById) {
        if (requiresGraph(definition.engine())) {
            String startNode = hasText(definition.startNodeId())
                    ? definition.startNodeId()
                    : nodes.isEmpty() ? "" : nodes.get(0).nodeId();
            if (!nodeIds.contains(startNode)) {
                throw new IllegalArgumentException(
                        "Agent 定义 start 节点不存在：" + startNode);
            }
        }
        Set<String> edgeKeys = new HashSet<>();
        Map<String, AgentGraphDefinition.Edge> edgeByKey = new HashMap<>();
        for (AgentGraphDefinition.Edge edge : definition.edges()) {
            validateEdge(edge, nodeIds, nodeById, edgeKeys, edgeByKey);
        }
        validateLoops(definition, nodeIds, edgeKeys, edgeByKey, nodeById);
        if (requiresGraph(definition.engine())) {
            validateGraphStructure(definition, nodes, nodeById);
        }
    }

    public boolean requiresGraph(String engine) {
        String normalized = normalizeEngine(engine, "GRAPH");
        return "GRAPH".equals(normalized)
                || "HYBRID".equals(normalized)
                || "SPRING_AI_ALIBABA_STATE_GRAPH".equals(normalized)
                || "CONTROLLED_GRAPH".equals(normalized);
    }

    private void validateEdge(
            AgentGraphDefinition.Edge edge,
            Set<String> nodeIds,
            Map<String, AgentGraphDefinition.Node> nodeById,
            Set<String> edgeKeys,
            Map<String, AgentGraphDefinition.Edge> edgeByKey) {
        if (edge == null
                || !hasText(edge.fromNodeId())
                || !hasText(edge.toNodeId())) {
            throw new IllegalArgumentException("存在缺少 from/to 的连线");
        }
        if (edge.fromNodeId().equals(edge.toNodeId())) {
            throw new IllegalArgumentException("连线不能自环：" + edge.fromNodeId());
        }
        if (!nodeIds.contains(edge.fromNodeId()) || !nodeIds.contains(edge.toNodeId())) {
            throw new IllegalArgumentException(
                    "边 " + edge.fromNodeId() + " -> " + edge.toNodeId()
                            + " 引用了不存在的节点");
        }
        String edgeKey = edgeKey(edge.fromNodeId(), edge.toNodeId());
        edgeKeys.add(edgeKey);
        edgeByKey.put(edgeKey, edge);
        AgentWorkflowRouteMode.fromPublishedName(
                edge.conditionType(),
                "default".equalsIgnoreCase(edge.conditionType()));
        if (edge.feedback()) {
            requireRouterSource(edge, nodeById, "回边必须从 Router 节点发出：");
        }
        if (isRouteCondition(edge)) {
            requireRouterSource(edge, nodeById, "路由条件边必须从 Router 节点发出：");
        }
    }

    private void requireRouterSource(
            AgentGraphDefinition.Edge edge,
            Map<String, AgentGraphDefinition.Node> nodeById,
            String message) {
        AgentGraphDefinition.Node fromNode = nodeById.get(edge.fromNodeId());
        if (fromNode == null || !"ROUTER".equals(nodePolicy.normalizedType(fromNode))) {
            throw new IllegalArgumentException(
                    message + edge.fromNodeId() + " -> " + edge.toNodeId());
        }
    }

    private void validateGraphStructure(
            AgentGraphDefinition definition,
            List<AgentGraphDefinition.Node> nodes,
            Map<String, AgentGraphDefinition.Node> nodeById) {
        List<String> startNodes = nodes.stream()
                .filter(node -> "START".equals(nodePolicy.normalizedType(node)))
                .map(AgentGraphDefinition.Node::nodeId)
                .toList();
        List<String> endNodes = nodes.stream()
                .filter(node -> "END".equals(nodePolicy.normalizedType(node)))
                .map(AgentGraphDefinition.Node::nodeId)
                .toList();
        if (startNodes.size() != 1) {
            throw new IllegalArgumentException("Graph 必须且只能包含一个 START 节点");
        }
        if (endNodes.size() != 1) {
            throw new IllegalArgumentException("Graph 必须且只能包含一个 END 节点");
        }
        String startNodeId = hasText(definition.startNodeId())
                ? definition.startNodeId()
                : startNodes.get(0);
        if (!startNodes.get(0).equals(startNodeId)) {
            throw new IllegalArgumentException(
                    "Agent 定义 startNodeId 必须指向 START 节点：" + startNodes.get(0));
        }
        String endNodeId = endNodes.get(0);
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        for (AgentGraphDefinition.Edge edge : definition.edges()) {
            outgoing.computeIfAbsent(edge.fromNodeId(), ignored -> new ArrayList<>())
                    .add(edge.toNodeId());
            incoming.computeIfAbsent(edge.toNodeId(), ignored -> new ArrayList<>())
                    .add(edge.fromNodeId());
        }
        if (!incoming.getOrDefault(startNodeId, List.of()).isEmpty()) {
            throw new IllegalArgumentException("START 节点不能有入边：" + startNodeId);
        }
        if (!outgoing.getOrDefault(endNodeId, List.of()).isEmpty()) {
            throw new IllegalArgumentException("END 节点不能有出边：" + endNodeId);
        }
        for (AgentGraphDefinition.Node node : nodes) {
            if (!endNodeId.equals(node.nodeId())
                    && outgoing.getOrDefault(node.nodeId(), List.of()).isEmpty()) {
                throw new IllegalArgumentException(
                        "非 END 节点必须显式连接后续节点：" + node.nodeId());
            }
        }
        Set<String> reachableFromStart = traverse(startNodeId, outgoing);
        List<String> unreachable = nodeById.keySet().stream()
                .filter(nodeId -> !reachableFromStart.contains(nodeId))
                .sorted()
                .toList();
        if (!unreachable.isEmpty()) {
            throw new IllegalArgumentException(
                    "存在无法从 START 到达的节点：" + String.join(",", unreachable));
        }
        Set<String> canReachEnd = traverse(endNodeId, incoming);
        List<String> nonConvergent = nodeById.keySet().stream()
                .filter(nodeId -> !canReachEnd.contains(nodeId))
                .sorted()
                .toList();
        if (!nonConvergent.isEmpty()) {
            throw new IllegalArgumentException(
                    "存在无法收敛到 END 的节点：" + String.join(",", nonConvergent));
        }
    }

    private Set<String> traverse(String start, Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            adjacency.getOrDefault(current, List.of()).stream()
                    .filter(this::hasText)
                    .forEach(queue::addLast);
        }
        return visited;
    }

    private void validateLoops(
            AgentGraphDefinition definition,
            Set<String> nodeIds,
            Set<String> edgeKeys,
            Map<String, AgentGraphDefinition.Edge> edgeByKey,
            Map<String, AgentGraphDefinition.Node> nodeById) {
        for (AgentGraphDefinition.Loop loop : definition.loops()) {
            if (loop == null || !hasText(loop.loopId())) {
                throw new IllegalArgumentException("存在缺少 loopId 的循环策略");
            }
            for (String nodeId : loop.nodeIds()) {
                if (hasText(nodeId) && !nodeIds.contains(nodeId)) {
                    throw new IllegalArgumentException(
                            "循环策略 " + loop.loopId()
                                    + " 引用了不存在的节点：" + nodeId);
                }
            }
            if (loop.maxRounds() != null
                    && (loop.maxRounds() < 1 || loop.maxRounds() > 20)) {
                throw new IllegalArgumentException(
                        "循环策略 " + loop.loopId()
                                + " maxRounds 必须在 1-20 之间");
            }
            for (String feedbackEdge : loop.feedbackEdges()) {
                String normalizedEdge = edgeKey(feedbackEdge);
                if (hasText(feedbackEdge) && !edgeKeys.contains(normalizedEdge)) {
                    throw new IllegalArgumentException(
                            "循环策略 " + loop.loopId()
                                    + " 引用了不存在的回边：" + feedbackEdge);
                }
                AgentGraphDefinition.Edge edge = edgeByKey.get(normalizedEdge);
                if (edge != null) {
                    AgentGraphDefinition.Node fromNode = nodeById.get(edge.fromNodeId());
                    if (fromNode == null
                            || !"ROUTER".equals(nodePolicy.normalizedType(fromNode))) {
                        throw new IllegalArgumentException(
                                "循环策略 " + loop.loopId()
                                        + " 的回边必须从 Router 节点发出：" + feedbackEdge);
                    }
                }
            }
            if (hasText(loop.exitEdge()) && !edgeKeys.contains(edgeKey(loop.exitEdge()))) {
                throw new IllegalArgumentException(
                        "循环策略 " + loop.loopId()
                                + " exitEdge 不存在：" + loop.exitEdge());
            }
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

    private String normalizeConditionType(String value) {
        if (!hasText(value)) return "always";
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private boolean isRouteCondition(AgentGraphDefinition.Edge edge) {
        String conditionType = normalizeConditionType(edge.conditionType());
        return "route_match".equals(conditionType)
                || "review_decision".equals(conditionType);
    }

    private String edgeKey(String from, String to) {
        return normalizeEdgeKey(text(from) + "->" + text(to));
    }

    private String edgeKey(String edge) {
        return normalizeEdgeKey(edge);
    }

    private String normalizeEdgeKey(String edge) {
        if (!hasText(edge)) return "";
        String[] parts = edge.split("->", 2);
        if (parts.length != 2) return edge.trim();
        return parts[0].trim() + "->" + parts[1].trim();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
