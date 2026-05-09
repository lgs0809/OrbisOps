package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Coordinates loop definitions with per-run round state. */
final class OpsGraphFeedbackLoopPolicy {

    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphLoopDefinitionPolicy definitionPolicy;
    private final OpsGraphLoopRoundState roundState;

    OpsGraphFeedbackLoopPolicy(
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator) {
        if (routingPolicy == null || conditionEvaluator == null) {
            throw new IllegalArgumentException("GRAPH_FEEDBACK_LOOP_DEPENDENCIES_REQUIRED");
        }
        this.routingPolicy = routingPolicy;
        this.definitionPolicy = new OpsGraphLoopDefinitionPolicy(
                routingPolicy, conditionEvaluator);
        this.roundState = new OpsGraphLoopRoundState();
    }

    int recursionLimit(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            List<OpsWorkflowNode> nodes) {
        return definitionPolicy.recursionLimit(definition, request, nodes);
    }

    boolean enabled(OpsAgentDefinition definition) {
        return definitionPolicy.enabled(definition);
    }

    boolean hasAvailableEdgeForSource(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            String source) {
        String expectedSource = routingPolicy.normalizeSource(source);
        if (definition == null || !StringUtils.hasText(expectedSource)) {
            return false;
        }
        Map<String, OpsWorkflowNode> nodeById = Optional.ofNullable(definition.getNodes())
                .orElse(List.of()).stream()
                .filter(node -> node != null && StringUtils.hasText(node.getNodeId()))
                .collect(Collectors.toMap(
                        OpsWorkflowNode::getNodeId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return Optional.ofNullable(definition.getEdges()).orElse(List.of()).stream()
                .filter(edge -> edge != null
                        && definitionPolicy.forEdge(definition, edge).isPresent())
                .filter(edge -> nodeById.containsKey(edge.getTo()))
                .filter(edge -> expectedSource.equals(
                        routingPolicy.routeConditionSource(edge)))
                .anyMatch(edge -> definitionPolicy.forEdge(definition, edge)
                        .map(loop -> roundState.round(state, loop.getLoopId())
                                < definitionPolicy.effectiveMaxRounds(
                                loop, definition, request))
                        .orElse(false));
    }

    RoundStatus primaryRoundStatus(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        OpsLoopPolicy loop = Optional.ofNullable(definition.getLoops())
                .orElse(List.of()).stream()
                .filter(policy -> policy != null
                        && StringUtils.hasText(policy.getLoopId()))
                .filter(policy -> policy.getFeedbackEdges() != null
                        && !policy.getFeedbackEdges().isEmpty())
                .findFirst()
                .orElse(null);
        if (loop == null) {
            OpsGraphEdge feedbackEdge = Optional.ofNullable(definition.getEdges())
                    .orElse(List.of()).stream()
                    .filter(edge -> Boolean.TRUE.equals(edge.getFeedback()))
                    .findFirst()
                    .orElse(null);
            if (feedbackEdge != null) {
                String loopId = definitionPolicy.graphEdgeKey(
                        feedbackEdge.getFrom(), feedbackEdge.getTo());
                return new RoundStatus(
                        loopId,
                        roundState.round(state, loopId),
                        routingPolicy.maxMainRounds(definition, request));
            }
            return new RoundStatus(
                    "default",
                    roundState.legacyReviewRound(state),
                    routingPolicy.maxMainRounds(definition, request));
        }
        return new RoundStatus(
                loop.getLoopId(),
                roundState.round(state, loop.getLoopId()),
                definitionPolicy.effectiveMaxRounds(loop, definition, request));
    }

    boolean allExhausted(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        List<OpsLoopPolicy> loops = definitionPolicy.feedbackLoopPolicies(definition);
        return !loops.isEmpty() && loops.stream()
                .allMatch(loop -> roundState.round(state, loop.getLoopId())
                        >= definitionPolicy.effectiveMaxRounds(
                        loop, definition, request));
    }

    Selection selectRoutes(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            List<OpsGraphEdge> selectedEdges) {
        List<String> selectedConditions = new ArrayList<>();
        Set<String> incrementedLoopIds = new LinkedHashSet<>();
        Map<String, Integer> decisionLoopRounds = new HashMap<>();
        String exhaustedExitCondition = "";
        for (OpsGraphEdge edge : selectedEdges) {
            Optional<OpsLoopPolicy> loop = definitionPolicy.forEdge(definition, edge);
            if (loop.isPresent()) {
                String loopId = loop.get().getLoopId();
                int currentRound = decisionLoopRounds.computeIfAbsent(
                        loopId,
                        key -> roundState.round(state, key));
                int maxRounds = definitionPolicy.effectiveMaxRounds(
                        loop.get(), definition, request);
                if (currentRound >= maxRounds) {
                    if (!StringUtils.hasText(exhaustedExitCondition)) {
                        exhaustedExitCondition = definitionPolicy.exhaustedExitCondition(
                                definition,
                                loop.get(),
                                edge.getFrom());
                    }
                    continue;
                }
                if (incrementedLoopIds.add(loopId)) {
                    roundState.increment(state, loopId);
                }
            }
            selectedConditions.add(edge.getCondition());
        }
        return new Selection(selectedConditions, exhaustedExitCondition);
    }

    Map<String, Object> edgeMetadata(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            OpsGraphEdge edge) {
        Optional<OpsLoopPolicy> loop = definitionPolicy.forEdge(definition, edge);
        if (loop.isEmpty()) {
            return Map.of();
        }
        String loopId = loop.get().getLoopId();
        int usedRounds = roundState.round(state, loopId);
        int maxRounds = definitionPolicy.effectiveMaxRounds(
                loop.get(), definition, request);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("loopId", loopId);
        metadata.put("usedRounds", usedRounds);
        metadata.put("maxRounds", maxRounds);
        metadata.put("remainingRounds", Math.max(0, maxRounds - usedRounds));
        metadata.put("exhausted", usedRounds >= maxRounds);
        metadata.put("exitEdge", value(loop.get().getExitEdge()));
        return metadata;
    }

    void addExitMappings(
            OpsAgentDefinition definition,
            String fromNodeId,
            List<OpsGraphEdge> outgoing,
            Optional<OpsGraphEdge> defaultEdge,
            Map<String, String> mapping) {
        definitionPolicy.addExitMappings(
                definition, fromNodeId, outgoing, defaultEdge, mapping);
    }

    Optional<OpsLoopPolicy> forEdge(
            OpsAgentDefinition definition,
            OpsGraphEdge edge) {
        return definitionPolicy.forEdge(definition, edge);
    }

    int round(OverAllState state, String loopId) {
        return roundState.round(state, loopId);
    }

    int effectiveMaxRounds(
            OpsLoopPolicy loop,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request) {
        return definitionPolicy.effectiveMaxRounds(loop, definition, request);
    }

    String graphEdgeKey(String from, String to) {
        return definitionPolicy.graphEdgeKey(from, to);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record RoundStatus(String loopId, int currentRound, int maxRounds) {
    }

    record Selection(
            List<String> selectedConditions,
            String exhaustedExitCondition) {
    }
}
