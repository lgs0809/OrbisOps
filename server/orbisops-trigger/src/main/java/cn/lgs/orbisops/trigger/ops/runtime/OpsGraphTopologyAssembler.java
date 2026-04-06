package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thin facade over Graph compilation, route selection and feedback-loop policy. */
final class OpsGraphTopologyAssembler {

    private final OpsGraphFeedbackLoopPolicy feedbackLoopPolicy;
    private final OpsGraphRouteSelector routeSelector;
    private final OpsGraphTopologyCompiler topologyCompiler;

    OpsGraphTopologyAssembler(
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator,
            OpsGraphRuntimeStateManager runtimeStateManager,
            OpsGraphFeedbackLoopPolicy feedbackLoopPolicy) {
        if (routingPolicy == null
                || conditionEvaluator == null
                || runtimeStateManager == null
                || feedbackLoopPolicy == null) {
            throw new IllegalArgumentException("GRAPH_TOPOLOGY_DEPENDENCIES_REQUIRED");
        }
        this.feedbackLoopPolicy = feedbackLoopPolicy;
        OpsRuntimeNodeExecutionPolicy nodeExecutionPolicy =
                new OpsRuntimeNodeExecutionPolicy(routingPolicy);
        this.routeSelector = new OpsGraphRouteSelector(
                routingPolicy,
                conditionEvaluator,
                runtimeStateManager,
                feedbackLoopPolicy,
                nodeExecutionPolicy);
        this.topologyCompiler = new OpsGraphTopologyCompiler(
                conditionEvaluator,
                feedbackLoopPolicy,
                nodeExecutionPolicy,
                routeSelector);
    }

    void addEdges(StateGraph graph,
                  OpsAgentDefinition definition,
                  OpsAgentRunRequestDTO request,
                  OpsAgentChatRequest runtimeRequest,
                  List<OpsWorkflowNode> nodes) throws GraphStateException {
        topologyCompiler.compile(graph, definition, request, runtimeRequest, nodes);
    }

    int recursionLimit(OpsAgentDefinition definition,
                       OpsAgentRunRequestDTO request,
                       List<OpsWorkflowNode> nodes) {
        return feedbackLoopPolicy.recursionLimit(definition, request, nodes);
    }

    boolean edgeActiveForPrompt(OpsGraphEdge edge, OverAllState state) {
        return routeSelector.edgeActiveForPrompt(edge, state);
    }

    boolean feedbackLoopEnabled(OpsAgentDefinition definition) {
        return feedbackLoopPolicy.enabled(definition);
    }

    boolean hasAvailableFeedbackEdgeForSource(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            String source) {
        return feedbackLoopPolicy.hasAvailableEdgeForSource(
                definition, request, state, source);
    }

    LoopRoundStatus primaryFeedbackLoopRoundStatus(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        OpsGraphFeedbackLoopPolicy.RoundStatus status =
                feedbackLoopPolicy.primaryRoundStatus(definition, request, state);
        return new LoopRoundStatus(
                status.loopId(), status.currentRound(), status.maxRounds());
    }

    boolean allFeedbackLoopsExhausted(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        return feedbackLoopPolicy.allExhausted(definition, request, state);
    }

    String routingChoices(OpsAgentDefinition definition,
                          String inputKey,
                          OverAllState state,
                          OpsAgentRunRequestDTO request) {
        return OpsGraphEdgePromptContext.routerChoiceContext(
                definition,
                inputKey,
                edge -> routeSelector.edgeActiveForPrompt(edge, state),
                edge -> edgeRuntimeMetadata(definition, request, state, edge));
    }

    boolean marksGraphCompletedOnSuccess(String type) {
        return "REPORT".equals(type)
                || "NOTIFY".equals(type)
                || "END".equals(type);
    }

    Optional<OpsLoopPolicy> feedbackLoopForEdge(
            OpsAgentDefinition definition,
            OpsGraphEdge edge) {
        return feedbackLoopPolicy.forEdge(definition, edge);
    }

    int loopRound(OverAllState state, String loopId) {
        return feedbackLoopPolicy.round(state, loopId);
    }

    int effectiveLoopMaxRounds(OpsLoopPolicy loop,
                               OpsAgentDefinition definition,
                               OpsAgentRunRequestDTO request) {
        return feedbackLoopPolicy.effectiveMaxRounds(loop, definition, request);
    }

    String graphEdgeKey(String from, String to) {
        return feedbackLoopPolicy.graphEdgeKey(from, to);
    }

    List<String> routeConditions(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            List<OpsGraphEdge> conditionalEdges,
            OpsWorkflowNode fromNode,
            String terminalNodeId,
            boolean parallelSelection,
            OpsAgentChatRequest runtimeRequest) {
        return routeSelector.select(
                definition,
                request,
                state,
                conditionalEdges,
                fromNode,
                terminalNodeId,
                parallelSelection,
                runtimeRequest);
    }

    Map<String, Object> edgeRuntimeMetadata(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state,
            OpsGraphEdge edge) {
        return feedbackLoopPolicy.edgeMetadata(definition, request, state, edge);
    }

    record LoopRoundStatus(String loopId, int currentRound, int maxRounds) {
        boolean exhausted() {
            return currentRound >= maxRounds;
        }

        int nextRound() {
            return Math.max(1, currentRound + 1);
        }
    }
}
