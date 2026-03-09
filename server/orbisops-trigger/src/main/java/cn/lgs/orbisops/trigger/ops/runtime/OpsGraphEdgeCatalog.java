package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Provides deterministic graph edge and node projections for prompt rendering. */
final class OpsGraphEdgeCatalog {

    private OpsGraphEdgeCatalog() {
    }

    static List<OpsGraphEdge> incomingEdges(
            OpsAgentDefinition definition,
            OpsWorkflowNode node) {
        String nodeId = node == null ? "" : node.getNodeId();
        return Optional.ofNullable(
                definition == null ? null : definition.getEdges())
                .orElse(List.of()).stream()
                .filter(edge -> edge != null && nodeId.equals(edge.getTo()))
                .toList();
    }

    static List<OpsGraphEdge> outgoingEdges(
            OpsAgentDefinition definition,
            OpsWorkflowNode node) {
        String nodeId = node == null ? "" : node.getNodeId();
        return Optional.ofNullable(
                definition == null ? null : definition.getEdges())
                .orElse(List.of()).stream()
                .filter(edge -> edge != null && nodeId.equals(edge.getFrom()))
                .toList();
    }

    static List<OpsGraphEdge> selectedEdges(
            List<OpsGraphEdge> edges,
            Predicate<OpsGraphEdge> activeMatcher) {
        if (activeMatcher == null || edges.isEmpty()) return edges;
        List<OpsGraphEdge> activeEdges = edges.stream()
                .filter(activeMatcher)
                .toList();
        return activeEdges.isEmpty() ? edges : activeEdges;
    }

    static Map<String, OpsWorkflowNode> nodeById(OpsAgentDefinition definition) {
        return Optional.ofNullable(
                definition == null ? null : definition.getNodes())
                .orElse(List.of()).stream()
                .filter(node -> node != null && StringUtils.hasText(node.getNodeId()))
                .collect(Collectors.toMap(
                        OpsWorkflowNode::getNodeId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }
}
