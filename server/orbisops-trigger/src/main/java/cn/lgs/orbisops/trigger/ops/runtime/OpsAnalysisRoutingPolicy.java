package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Stable facade over bounded analysis routing policies. */
final class OpsAnalysisRoutingPolicy {

    static final String REVIEW_MODE_NONE =
            OpsAnalysisReviewRoutingPolicy.REVIEW_MODE_NONE;
    static final String REVIEW_MODE_BATCH =
            OpsAnalysisReviewRoutingPolicy.REVIEW_MODE_BATCH;
    static final String REVIEW_MODE_IMMEDIATE =
            OpsAnalysisReviewRoutingPolicy.REVIEW_MODE_IMMEDIATE;

    private final OpsAnalysisSourcePolicy sourcePolicy;
    private final OpsAnalysisTaskProjectionPolicy taskProjectionPolicy;
    private final OpsAnalysisReviewRoutingPolicy reviewRoutingPolicy;
    private final OpsAnalysisRouterSelectionPolicy routerSelectionPolicy;

    OpsAnalysisRoutingPolicy() {
        this.sourcePolicy = new OpsAnalysisSourcePolicy();
        this.taskProjectionPolicy = new OpsAnalysisTaskProjectionPolicy(sourcePolicy);
        this.reviewRoutingPolicy = new OpsAnalysisReviewRoutingPolicy(sourcePolicy);
        this.routerSelectionPolicy = new OpsAnalysisRouterSelectionPolicy(
                sourcePolicy, taskProjectionPolicy);
    }

    String normalizeSource(String source) {
        return sourcePolicy.normalizeSource(source);
    }

    boolean isInvestigationRoute(String source) {
        return sourcePolicy.isInvestigationRoute(source);
    }

    String routeConditionSource(OpsGraphEdge edge) {
        return sourcePolicy.routeConditionSource(edge);
    }

    String incomingRouteKey(OpsAgentDefinition definition, OpsWorkflowNode node) {
        return sourcePolicy.incomingRouteKey(definition, node);
    }

    String resolveSource(OpsAgentDefinition definition,
                         OpsWorkflowNode node,
                         String type) {
        return sourcePolicy.resolveSource(definition, node, type);
    }

    String agentRole(OpsWorkflowNode node) {
        return sourcePolicy.agentRole(node);
    }

    Optional<OpsAnalysisResponseDTO.InvestigationTaskDTO> selectedTask(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            String source) {
        return taskProjectionPolicy.selectedTask(plan, source);
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO canonicalTask(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task) {
        return taskProjectionPolicy.canonicalTask(task);
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO graphNodeTask(
            OpsWorkflowNode node,
            String type,
            String source) {
        return taskProjectionPolicy.graphNodeTask(node, type, source);
    }

    List<String> selectedRoutes(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        return taskProjectionPolicy.selectedRoutes(plan);
    }

    Set<String> plannedSources(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        return taskProjectionPolicy.plannedSources(plan);
    }

    Set<String> executedSources(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        return taskProjectionPolicy.executedSources(results);
    }

    String reviewDecisionForRoutes(List<String> routes) {
        return reviewRoutingPolicy.reviewDecisionForRoutes(routes);
    }

    String reviewMode(OpsAgentDefinition definition) {
        return reviewRoutingPolicy.reviewMode(definition);
    }

    String reviewMode(OpsAgentDefinition definition, OpsWorkflowNode reviewNode) {
        return reviewRoutingPolicy.reviewMode(definition, reviewNode);
    }

    int maxMainRounds(OpsAgentDefinition definition, OpsAgentRunRequestDTO request) {
        return reviewRoutingPolicy.maxMainRounds(definition, request);
    }

    List<String> nodeSkills(OpsAgentDefinition definition, OpsWorkflowNode node) {
        return taskProjectionPolicy.nodeSkills(definition, node);
    }

    List<OpsAnalysisResponseDTO.InvestigationTaskDTO> canonicalUnexecutedTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition) {
        return taskProjectionPolicy.canonicalUnexecutedTasks(
                plan, existingResults, definition);
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO enrichWithGraphHandoff(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            Predicate<OpsGraphEdge> edgeActive) {
        return taskProjectionPolicy.enrichWithGraphHandoff(
                task, definition, node, edgeActive);
    }

    OpsAnalysisResponseDTO.InvestigationTaskDTO enrichWithFeedbackHandoff(
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentDefinition definition) {
        return taskProjectionPolicy.enrichWithFeedbackHandoff(task, definition);
    }

    List<String> selectedRoutesForRouter(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            OverAllState graphState,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsAgentChatRequest request) {
        return routerSelectionPolicy.selectedRoutesForRouter(
                definition, node, graphState, plan, request);
    }

    Set<String> routeConstraint(OpsAgentChatRequest request, String key) {
        return routerSelectionPolicy.routeConstraint(request, key);
    }

    boolean allowsSource(String source,
                         Set<String> allowedSources,
                         Set<String> excludedCapabilities) {
        return routerSelectionPolicy.allowsSource(
                source, allowedSources, excludedCapabilities);
    }

    boolean allowsEdge(OpsGraphEdge edge,
                       Set<String> allowedSources,
                       Set<String> excludedCapabilities,
                       String normalizedConditionType) {
        return routerSelectionPolicy.allowsEdge(
                edge,
                allowedSources,
                excludedCapabilities,
                normalizedConditionType);
    }

    List<String> routesFromRouterInput(Object input, Set<String> allowedRoutes) {
        return routerSelectionPolicy.routesFromRouterInput(input, allowedRoutes);
    }

    boolean routeTextContains(String expected, String text) {
        return routerSelectionPolicy.routeTextContains(expected, text);
    }
}
