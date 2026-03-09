package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.agentdefinition.rule.WorkflowRouteSelectionMode;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Applies runtime route gates and selects Graph edge conditions. */
@Slf4j
final class OpsGraphRouteSelector {

    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphConditionEvaluator conditionEvaluator;
    private final OpsGraphRuntimeStateManager runtimeStateManager;
    private final OpsGraphFeedbackLoopPolicy feedbackLoopPolicy;
    private final OpsGraphRouteSelectionPolicy selectionPolicy;

    OpsGraphRouteSelector(OpsAnalysisRoutingPolicy routingPolicy,
                          OpsGraphConditionEvaluator conditionEvaluator,
                          OpsGraphRuntimeStateManager runtimeStateManager,
                          OpsGraphFeedbackLoopPolicy feedbackLoopPolicy,
                          OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy) {
        this.routingPolicy = routingPolicy;
        this.conditionEvaluator = conditionEvaluator;
        this.runtimeStateManager = runtimeStateManager;
        this.feedbackLoopPolicy = feedbackLoopPolicy;
        this.selectionPolicy = new OpsGraphRouteSelectionPolicy(
                routingPolicy, conditionEvaluator, nodeExecutionPolicy);
    }

    List<String> select(OpsAgentDefinition definition,
                        OpsAgentRunRequestDTO request,
                        OverAllState state,
                        List<OpsGraphEdge> conditionalEdges,
                        OpsWorkflowNode fromNode,
                        String terminalNodeId,
                        boolean parallelSelection,
                        OpsAgentChatRequest runtimeRequest) {
        if (runtimeStateManager.stateBoolean(
                state, OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY)
                && StringUtils.hasText(terminalNodeId)
                && !selectionPolicy.allowsRoutingAfterGraphCompletion(fromNode)) {
            return List.of("__end__");
        }
        Set<String> requestAllowedByIntent = routingPolicy.routeConstraint(
                runtimeRequest, "allowedInvestigationSources");
        Set<String> requestExcludedByIntent = routingPolicy.routeConstraint(
                runtimeRequest, "excludedCapabilities");
        Set<String> allowedByIntent = requestAllowedByIntent.isEmpty()
                ? conditionEvaluator.stateRouteConstraint(
                state, "intentAllowedRoutes", selectionPolicy.conditionContext())
                : requestAllowedByIntent;
        Set<String> excludedByIntent = requestExcludedByIntent.isEmpty()
                ? conditionEvaluator.stateRouteConstraint(
                state, "intentExcludedRoutes", selectionPolicy.conditionContext())
                : requestExcludedByIntent;
        Set<String> executedSources = runtimeStateManager.graphStateExecutedSources(
                state, routingPolicy);
        boolean singleExplicitSource = allowedByIntent.size() == 1;
        boolean singleExplicitSourceAlreadyExecuted = singleExplicitSource
                && (runtimeStateManager.stateBoolean(
                state, OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY)
                || runtimeStateManager.investigationExecuted(runtimeRequest)
                || !executedSources.isEmpty());
        log.info(
                "Graph route gate from={}, allowed={}, excluded={}, investigationExecuted={}, executedSources={}",
                fromNode == null ? "" : fromNode.getNodeId(),
                allowedByIntent,
                excludedByIntent,
                runtimeStateManager.stateBoolean(
                        state, OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY)
                        || runtimeStateManager.investigationExecuted(runtimeRequest),
                executedSources);
        List<OpsGraphEdge> eligibleEdges = conditionalEdges.stream()
                .filter(edge -> routingPolicy.allowsEdge(
                        edge,
                        allowedByIntent,
                        excludedByIntent,
                        conditionEvaluator.normalizeConditionType(
                                edge == null ? null : edge.getConditionType())))
                .filter(edge -> !singleExplicitSourceAlreadyExecuted
                        || !routingPolicy.isInvestigationRoute(
                        routingPolicy.routeConditionSource(edge)))
                .sorted(edgeOrder())
                .toList();
        WorkflowRouteSelectionMode routeMode = selectionPolicy.routeMode(fromNode, parallelSelection);
        if (routeMode == WorkflowRouteSelectionMode.DEFAULT_ONLY) {
            return selectionPolicy.noMatchRoutes(fromNode);
        }
        List<OpsGraphEdge> matchedEdges = eligibleEdges.stream()
                .filter(edge -> conditionEvaluator.matches(
                        edge, state, selectionPolicy.conditionContext()))
                .toList();
        if (matchedEdges.isEmpty()) {
            return selectionPolicy.noMatchRoutes(fromNode);
        }
        List<OpsGraphEdge> selectedEdges = switch (routeMode) {
            case FIRST_MATCH -> List.of(matchedEdges.get(0));
            case FAN_OUT -> matchedEdges;
            case ALL_MATCH -> matchedEdges.size() == eligibleEdges.size()
                    ? matchedEdges
                    : List.of();
            case DEFAULT_ONLY -> List.of();
        };
        if (selectedEdges.isEmpty()) {
            return selectionPolicy.noMatchRoutes(fromNode);
        }
        OpsGraphFeedbackLoopPolicy.Selection selection =
                feedbackLoopPolicy.selectRoutes(
                        definition, request, state, selectedEdges);
        if (selection.selectedConditions().isEmpty()
                && StringUtils.hasText(selection.exhaustedExitCondition())) {
            return List.of(selection.exhaustedExitCondition());
        }
        return selection.selectedConditions().isEmpty()
                ? selectionPolicy.noMatchRoutes(fromNode)
                : selection.selectedConditions();
    }

    String selectSingle(OpsAgentDefinition definition,
                        OpsAgentRunRequestDTO request,
                        OverAllState state,
                        List<OpsGraphEdge> conditionalEdges,
                        OpsWorkflowNode fromNode,
                        String terminalNodeId,
                        OpsAgentChatRequest runtimeRequest) {
        List<String> routes = select(
                definition,
                request,
                state,
                conditionalEdges,
                fromNode,
                terminalNodeId,
                false,
                runtimeRequest);
        return routes.isEmpty() ? "__default__" : routes.get(0);
    }

    boolean edgeActiveForPrompt(OpsGraphEdge edge, OverAllState state) {
        if (edge == null) {
            return false;
        }
        if (conditionEvaluator.isAlwaysEdge(edge)) {
            return true;
        }
        if (state == null) {
            return false;
        }
        try {
            return conditionEvaluator.matches(
                    edge, state, selectionPolicy.conditionContext());
        } catch (Exception error) {
            log.debug(
                    "边交接上下文匹配失败，edge={} -> {}，conditionType={}，condition={}",
                    edge.getFrom(),
                    edge.getTo(),
                    edge.getConditionType(),
                    edge.getCondition(),
                    error);
            return false;
        }
    }

    boolean useParallelConditionalEdges(OpsWorkflowNode fromNode) {
        return selectionPolicy.useParallelConditionalEdges(fromNode);
    }

    private Comparator<OpsGraphEdge> edgeOrder() {
        return Comparator
                .comparingInt((OpsGraphEdge edge) ->
                        edge == null || edge.getPriority() == null ? 0 : edge.getPriority())
                .reversed()
                .thenComparing(this::stableEdgeId);
    }

    private String stableEdgeId(OpsGraphEdge edge) {
        if (edge == null) return "";
        if (StringUtils.hasText(edge.getEdgeId())) return edge.getEdgeId().trim();
        return value(edge.getFrom()) + "->" + value(edge.getTo())
                + "#" + value(edge.getConditionType())
                + "#" + value(edge.getCondition());
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
