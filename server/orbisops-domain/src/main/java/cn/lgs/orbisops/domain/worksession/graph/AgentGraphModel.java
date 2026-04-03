package cn.lgs.orbisops.domain.worksession.graph;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AgentGraphModel {

    private AgentGraphModel() {
    }

    public record Node(String nodeId, String type) {
        public Node {
            nodeId = required(nodeId, "AGENT_GRAPH_NODE_ID_REQUIRED");
            type = required(type, "AGENT_GRAPH_NODE_TYPE_REQUIRED").toUpperCase(Locale.ROOT);
        }

        public boolean terminal() {
            return "END".equals(type);
        }
    }

    public record Edge(String sourceNodeId,
                       String targetNodeId,
                       String route,
                       int priority,
                       boolean loopEdge) {
        public Edge {
            sourceNodeId = required(sourceNodeId, "AGENT_GRAPH_EDGE_SOURCE_REQUIRED");
            targetNodeId = required(targetNodeId, "AGENT_GRAPH_EDGE_TARGET_REQUIRED");
            route = text(route);
        }

        public EdgeKey key() {
            return new EdgeKey(sourceNodeId, targetNodeId);
        }
    }

    public record EdgeKey(String sourceNodeId, String targetNodeId) {
        public EdgeKey {
            sourceNodeId = required(sourceNodeId, "AGENT_GRAPH_EDGE_SOURCE_REQUIRED");
            targetNodeId = required(targetNodeId, "AGENT_GRAPH_EDGE_TARGET_REQUIRED");
        }
    }

    public record Definition(String graphId,
                             String startNodeId,
                             List<Node> nodes,
                             List<Edge> edges,
                             Set<EdgeKey> allowedLoopEdges) {
        public Definition {
            graphId = required(graphId, "AGENT_GRAPH_ID_REQUIRED");
            startNodeId = text(startNodeId);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            allowedLoopEdges = allowedLoopEdges == null ? Set.of() : Set.copyOf(allowedLoopEdges);
            if (nodes.isEmpty()) throw new IllegalArgumentException("AGENT_GRAPH_NODES_REQUIRED");
        }
    }

    public record Compiled(String graphId,
                           String startNodeId,
                           List<String> reachableNodeIds,
                           List<String> executionOrder,
                           List<String> terminalNodeIds,
                           List<Edge> loopEdges) {
        public Compiled {
            reachableNodeIds = List.copyOf(reachableNodeIds);
            executionOrder = List.copyOf(executionOrder);
            terminalNodeIds = List.copyOf(terminalNodeIds);
            loopEdges = List.copyOf(loopEdges);
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
