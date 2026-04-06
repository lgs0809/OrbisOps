package cn.lgs.orbisops.domain.worksession.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentGraphCompiler {

    public AgentGraphModel.Compiled compile(AgentGraphModel.Definition definition) {
        if (definition == null) throw new IllegalArgumentException("AGENT_GRAPH_DEFINITION_REQUIRED");
        Map<String, AgentGraphModel.Node> nodes = indexNodes(definition.nodes());
        String startNodeId = resolveStartNode(definition, nodes);
        List<AgentGraphModel.Edge> loopEdges = new ArrayList<>();
        Map<String, List<AgentGraphModel.Edge>> outgoing = indexEdges(definition, nodes, loopEdges);
        List<String> reachable = reachable(startNodeId, outgoing);
        if (reachable.size() != nodes.size()) {
            Set<String> unreachable = new LinkedHashSet<>(nodes.keySet());
            unreachable.removeAll(reachable);
            throw new IllegalStateException("AGENT_GRAPH_UNREACHABLE_NODES:" + String.join(",", unreachable));
        }
        List<String> terminals = nodes.values().stream()
                .filter(node -> node.terminal() || outgoing.getOrDefault(node.nodeId(), List.of()).stream()
                        .noneMatch(edge -> !edge.loopEdge()))
                .map(AgentGraphModel.Node::nodeId)
                .toList();
        if (terminals.isEmpty()) throw new IllegalStateException("AGENT_GRAPH_TERMINAL_REQUIRED");
        List<String> order = topologicalOrder(nodes.keySet(), definition.edges(), definition.allowedLoopEdges());
        return new AgentGraphModel.Compiled(definition.graphId(), startNodeId, reachable, order, terminals, loopEdges);
    }

    private Map<String, AgentGraphModel.Node> indexNodes(List<AgentGraphModel.Node> source) {
        Map<String, AgentGraphModel.Node> result = new LinkedHashMap<>();
        for (AgentGraphModel.Node node : source) {
            if (result.putIfAbsent(node.nodeId(), node) != null) {
                throw new IllegalArgumentException("AGENT_GRAPH_DUPLICATE_NODE:" + node.nodeId());
            }
        }
        return result;
    }

    private String resolveStartNode(AgentGraphModel.Definition definition,
                                    Map<String, AgentGraphModel.Node> nodes) {
        if (!definition.startNodeId().isBlank()) {
            if (!nodes.containsKey(definition.startNodeId())) {
                throw new IllegalArgumentException("AGENT_GRAPH_START_NODE_UNKNOWN:" + definition.startNodeId());
            }
            return definition.startNodeId();
        }
        List<String> starts = nodes.values().stream()
                .filter(node -> "START".equals(node.type()))
                .map(AgentGraphModel.Node::nodeId)
                .toList();
        if (starts.size() != 1) throw new IllegalStateException("AGENT_GRAPH_START_NODE_AMBIGUOUS");
        return starts.get(0);
    }

    private Map<String, List<AgentGraphModel.Edge>> indexEdges(AgentGraphModel.Definition definition,
                                                               Map<String, AgentGraphModel.Node> nodes,
                                                               List<AgentGraphModel.Edge> loopEdges) {
        Map<String, List<AgentGraphModel.Edge>> outgoing = new HashMap<>();
        Set<String> identities = new HashSet<>();
        for (AgentGraphModel.Edge edge : definition.edges()) {
            if (!nodes.containsKey(edge.sourceNodeId()) || !nodes.containsKey(edge.targetNodeId())) {
                throw new IllegalArgumentException("AGENT_GRAPH_EDGE_NODE_UNKNOWN:"
                        + edge.sourceNodeId() + "->" + edge.targetNodeId());
            }
            String identity = edge.sourceNodeId() + "\u0000" + edge.targetNodeId() + "\u0000" + edge.route();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("AGENT_GRAPH_DUPLICATE_EDGE:"
                        + edge.sourceNodeId() + "->" + edge.targetNodeId());
            }
            boolean loop = edge.loopEdge() || definition.allowedLoopEdges().contains(edge.key());
            AgentGraphModel.Edge normalized = loop == edge.loopEdge() ? edge : new AgentGraphModel.Edge(
                    edge.sourceNodeId(), edge.targetNodeId(), edge.route(), edge.priority(), true);
            outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(normalized);
            if (normalized.loopEdge()) loopEdges.add(normalized);
        }
        outgoing.values().forEach(list -> list.sort(Comparator
                .comparingInt(AgentGraphModel.Edge::priority).reversed()
                .thenComparing(AgentGraphModel.Edge::targetNodeId)));
        return outgoing;
    }

    private List<String> reachable(String startNodeId,
                                   Map<String, List<AgentGraphModel.Edge>> outgoing) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!seen.add(current)) continue;
            for (AgentGraphModel.Edge edge : outgoing.getOrDefault(current, List.of())) {
                queue.addLast(edge.targetNodeId());
            }
        }
        return List.copyOf(seen);
    }

    private List<String> topologicalOrder(Set<String> nodeIds,
                                          List<AgentGraphModel.Edge> edges,
                                          Set<AgentGraphModel.EdgeKey> allowedLoopEdges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        nodeIds.forEach(node -> indegree.put(node, 0));
        for (AgentGraphModel.Edge edge : edges) {
            if (edge.loopEdge() || allowedLoopEdges.contains(edge.key())) continue;
            outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId());
            indegree.compute(edge.targetNodeId(), (ignored, value) -> value == null ? 1 : value + 1);
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.entrySet().stream().filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey).sorted().forEach(queue::addLast);
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            order.add(current);
            for (String target : outgoing.getOrDefault(current, List.of())) {
                int remaining = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                if (remaining == 0) queue.addLast(target);
            }
        }
        if (order.size() != nodeIds.size()) {
            throw new IllegalStateException("AGENT_GRAPH_UNDECLARED_CYCLE");
        }
        return List.copyOf(order);
    }
}
