package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns feedback-loop declarations, edge identity, bounds and configured exits. */
final class OpsGraphLoopDefinitionPolicy {

    private static final String LOOP_EXIT_CONDITION_PREFIX = "__loop_exit__:";

    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphConditionEvaluator conditionEvaluator;

    OpsGraphLoopDefinitionPolicy(
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator) {
        if (routingPolicy == null || conditionEvaluator == null) {
            throw new IllegalArgumentException("GRAPH_LOOP_DEFINITION_DEPENDENCIES_REQUIRED");
        }
        this.routingPolicy = routingPolicy;
        this.conditionEvaluator = conditionEvaluator;
    }

    int recursionLimit(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            List<OpsWorkflowNode> nodes) {
        int nodeCount = Optional.ofNullable(nodes).orElse(List.of()).size();
        int edgeCount = Optional.ofNullable(definition.getEdges()).orElse(List.of()).size();
        int maxLoopRounds = Optional.ofNullable(definition.getLoops()).orElse(List.of()).stream()
                .map(loop -> effectiveMaxRounds(loop, definition, request))
                .max(Integer::compareTo)
                .orElse(routingPolicy.maxMainRounds(definition, request));
        return Math.max(
                4,
                nodeCount + edgeCount + 4
                        + Math.max(1, maxLoopRounds) * Math.max(1, nodeCount));
    }

    boolean enabled(OpsAgentDefinition definition) {
        boolean edgeMarkedFeedback = Optional.ofNullable(definition)
                .map(OpsAgentDefinition::getEdges)
                .orElse(List.of()).stream()
                .anyMatch(edge -> Boolean.TRUE.equals(edge.getFeedback()));
        if (edgeMarkedFeedback) {
            return true;
        }
        return Optional.ofNullable(definition)
                .map(OpsAgentDefinition::getLoops)
                .orElse(List.of()).stream()
                .anyMatch(loop -> loop != null
                        && loop.getFeedbackEdges() != null
                        && !loop.getFeedbackEdges().isEmpty());
    }

    Optional<OpsLoopPolicy> forEdge(
            OpsAgentDefinition definition,
            OpsGraphEdge edge) {
        if (definition == null || edge == null) {
            return Optional.empty();
        }
        String edgeKey = graphEdgeKey(edge.getFrom(), edge.getTo());
        List<OpsLoopPolicy> loops = Optional.ofNullable(definition.getLoops())
                .orElse(List.of());
        Optional<OpsLoopPolicy> explicitLoop = loops.stream()
                .filter(loop -> loop != null && StringUtils.hasText(loop.getLoopId()))
                .filter(loop -> Optional.ofNullable(loop.getFeedbackEdges())
                        .orElse(List.of()).stream()
                        .map(this::normalizeGraphEdgeKey)
                        .anyMatch(edgeKey::equals))
                .findFirst();
        if (explicitLoop.isPresent()) {
            return explicitLoop;
        }
        if (!Boolean.TRUE.equals(edge.getFeedback())) {
            return Optional.empty();
        }
        Optional<OpsLoopPolicy> nodeLoop = loops.stream()
                .filter(loop -> loop != null && StringUtils.hasText(loop.getLoopId()))
                .filter(loop -> Optional.ofNullable(loop.getNodes()).orElse(List.of())
                        .contains(edge.getFrom())
                        && Optional.ofNullable(loop.getNodes()).orElse(List.of())
                        .contains(edge.getTo()))
                .findFirst()
                .or(() -> loops.stream()
                        .filter(loop -> loop != null
                                && StringUtils.hasText(loop.getLoopId()))
                        .findFirst());
        return nodeLoop.or(() -> Optional.of(OpsLoopPolicy.builder()
                .loopId(graphEdgeKey(edge.getFrom(), edge.getTo()))
                .feedbackEdges(List.of(
                        graphEdgeKey(edge.getFrom(), edge.getTo())))
                .build()));
    }

    int effectiveMaxRounds(
            OpsLoopPolicy loop,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request) {
        Integer configured = Optional.ofNullable(request)
                .map(OpsAgentRunRequestDTO::getMaxRounds)
                .orElse(null);
        if (configured == null) {
            configured = loop == null ? null : loop.getMaxRounds();
        }
        if (configured == null) {
            configured = Optional.ofNullable(definition)
                    .map(OpsAgentDefinition::getDefaultMaxMainRounds)
                    .orElse(3);
        }
        return Math.max(1, Math.min(configured, 20));
    }

    void addExitMappings(
            OpsAgentDefinition definition,
            String fromNodeId,
            List<OpsGraphEdge> outgoing,
            Optional<OpsGraphEdge> defaultEdge,
            Map<String, String> mapping) {
        for (OpsLoopPolicy loop : Optional.ofNullable(definition.getLoops())
                .orElse(List.of())) {
            if (loop == null
                    || !StringUtils.hasText(loop.getLoopId())
                    || !containsFeedbackFrom(loop, fromNodeId)) {
                continue;
            }
            String exitTarget = exitTarget(loop, fromNodeId, outgoing)
                    .orElseGet(() -> defaultEdge
                            .map(OpsGraphEdge::getTo)
                            .orElse(null));
            if (StringUtils.hasText(exitTarget)) {
                mapping.put(exitCondition(loop.getLoopId()), exitTarget);
            }
        }
    }

    String exhaustedExitCondition(
            OpsAgentDefinition definition,
            OpsLoopPolicy loop,
            String fromNodeId) {
        boolean configuredLoop = Optional.ofNullable(definition.getLoops())
                .orElse(List.of()).stream()
                .filter(policy -> policy != null
                        && StringUtils.hasText(policy.getLoopId()))
                .anyMatch(policy -> policy.getLoopId().equals(loop.getLoopId()));
        if (!configuredLoop) {
            return "__default__";
        }
        List<OpsGraphEdge> outgoing = Optional.ofNullable(definition.getEdges())
                .orElse(List.of()).stream()
                .filter(edge -> fromNodeId.equals(edge.getFrom()))
                .toList();
        Optional<OpsGraphEdge> defaultEdge = outgoing.stream()
                .filter(conditionEvaluator::isDefaultEdge)
                .findFirst();
        return exitTarget(loop, fromNodeId, outgoing).isPresent()
                || defaultEdge.isPresent()
                ? exitCondition(loop.getLoopId())
                : "__default__";
    }

    List<OpsLoopPolicy> feedbackLoopPolicies(OpsAgentDefinition definition) {
        List<OpsLoopPolicy> configuredLoops = Optional.ofNullable(definition.getLoops())
                .orElse(List.of()).stream()
                .filter(loop -> loop != null && StringUtils.hasText(loop.getLoopId()))
                .filter(loop -> loop.getFeedbackEdges() != null
                        && !loop.getFeedbackEdges().isEmpty())
                .toList();
        if (!configuredLoops.isEmpty()) {
            return configuredLoops;
        }
        return Optional.ofNullable(definition.getEdges()).orElse(List.of()).stream()
                .filter(edge -> Boolean.TRUE.equals(edge.getFeedback()))
                .map(edge -> OpsLoopPolicy.builder()
                        .loopId(graphEdgeKey(edge.getFrom(), edge.getTo()))
                        .feedbackEdges(List.of(
                                graphEdgeKey(edge.getFrom(), edge.getTo())))
                        .build())
                .toList();
    }

    String graphEdgeKey(String from, String to) {
        return normalizeGraphEdgeKey(
                value(from).trim() + "->" + value(to).trim());
    }

    private boolean containsFeedbackFrom(
            OpsLoopPolicy loop,
            String fromNodeId) {
        String normalizedFrom = value(fromNodeId).trim();
        return Optional.ofNullable(loop.getFeedbackEdges()).orElse(List.of()).stream()
                .map(this::normalizeGraphEdgeKey)
                .anyMatch(edgeKey -> edgeKey.startsWith(normalizedFrom + "->"));
    }

    private Optional<String> exitTarget(
            OpsLoopPolicy loop,
            String fromNodeId,
            List<OpsGraphEdge> outgoing) {
        if (loop == null || !StringUtils.hasText(loop.getExitEdge())) {
            return Optional.empty();
        }
        String exitEdge = normalizeGraphEdgeKey(loop.getExitEdge());
        String prefix = value(fromNodeId).trim() + "->";
        if (!exitEdge.startsWith(prefix)) {
            return Optional.empty();
        }
        String target = exitEdge.substring(prefix.length()).trim();
        boolean declared = Optional.ofNullable(outgoing).orElse(List.of()).stream()
                .anyMatch(edge -> graphEdgeKey(
                        edge.getFrom(), edge.getTo()).equals(exitEdge));
        return declared && StringUtils.hasText(target)
                ? Optional.of(target)
                : Optional.empty();
    }

    private String exitCondition(String loopId) {
        return LOOP_EXIT_CONDITION_PREFIX + value(loopId).trim();
    }

    private String normalizeGraphEdgeKey(String edgeKey) {
        if (!StringUtils.hasText(edgeKey)) {
            return "";
        }
        String[] parts = edgeKey.split("->", 2);
        if (parts.length != 2) {
            return edgeKey.trim();
        }
        return parts[0].trim() + "->" + parts[1].trim();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
