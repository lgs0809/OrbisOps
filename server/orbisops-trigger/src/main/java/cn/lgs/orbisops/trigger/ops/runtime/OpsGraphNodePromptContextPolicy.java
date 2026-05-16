package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.Map;

/** Builds prompt and condition contexts without exposing coordinator internals. */
final class OpsGraphNodePromptContextPolicy {

    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;
    private final OpsGraphTopologyAssembler graphTopologyAssembler;
    private final OpsGraphConditionEvaluator conditionEvaluator;

    OpsGraphNodePromptContextPolicy(OpsAnalysisRoutingPolicy analysisRoutingPolicy,
                                    OpsGraphTopologyAssembler graphTopologyAssembler,
                                    OpsGraphConditionEvaluator conditionEvaluator) {
        this.analysisRoutingPolicy = analysisRoutingPolicy;
        this.graphTopologyAssembler = graphTopologyAssembler;
        this.conditionEvaluator = conditionEvaluator;
    }

    OpsRuntimePromptAssembler.ContextPolicy promptPolicy(OpsGraphNodeExecutionCoordinator.Hooks hooks) {
        return new OpsRuntimePromptAssembler.ContextPolicy() {
            @Override
            public String executionNodeType(OpsWorkflowNode node) {
                return hooks.executionNodeType(node);
            }

            @Override
            public String incomingRouteKey(OpsAgentDefinition definition, OpsWorkflowNode node) {
                return analysisRoutingPolicy.incomingRouteKey(definition, node);
            }

            @Override
            public String analysisAgentRole(OpsWorkflowNode node) {
                return analysisRoutingPolicy.agentRole(node);
            }

            @Override
            public boolean edgeActive(OpsGraphEdge edge, OverAllState state) {
                return graphTopologyAssembler.edgeActiveForPrompt(edge, state);
            }

            @Override
            public Map<String, Object> edgeRuntimeMetadata(
                    OpsAgentDefinition definition,
                    OverAllState state,
                    OpsGraphEdge edge) {
                return graphTopologyAssembler.edgeRuntimeMetadata(definition, null, state, edge);
            }
        };
    }

    boolean graphReviewDecisionIsFinalReport(OverAllState state) {
        if (state == null) {
            return false;
        }
        Object decision = state.value("review_decision").orElse(null);
        return conditionEvaluator.routeTextMatches(
                "final_report",
                String.valueOf(decision),
                conditionContext());
    }

    private OpsGraphConditionEvaluator.Context conditionContext() {
        return new OpsGraphConditionEvaluator.Context() {
            @Override
            public String normalizeAnalysisSource(String source) {
                return analysisRoutingPolicy.normalizeSource(source);
            }

            @Override
            public String routeConditionSource(OpsGraphEdge edge) {
                return analysisRoutingPolicy.routeConditionSource(edge);
            }

            @Override
            public boolean isInvestigationRoute(String source) {
                return analysisRoutingPolicy.isInvestigationRoute(source);
            }

            @Override
            public boolean hasSelectedAnalysisTask(
                    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                    String source) {
                return analysisRoutingPolicy.selectedTask(plan, source).isPresent();
            }
        };
    }
}
