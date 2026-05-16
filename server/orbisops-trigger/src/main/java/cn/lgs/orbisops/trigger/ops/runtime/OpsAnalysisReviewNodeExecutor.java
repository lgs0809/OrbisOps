package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Owns Analysis review, replan and graph feedback-loop decisions. */
final class OpsAnalysisReviewNodeExecutor {

    private final OpsMainAgentPlanner planner;
    private final OpsAnalysisPlanLoopCoordinator planLoopCoordinator;
    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphRuntimeStateManager graphStateManager;
    private final OpsGraphTopologyAssembler topologyAssembler;
    private final OpsAnalysisNodeLifecycle lifecycle;

    OpsAnalysisReviewNodeExecutor(OpsMainAgentPlanner planner,
                                  OpsAnalysisPlanLoopCoordinator planLoopCoordinator,
                                  OpsAnalysisRoutingPolicy routingPolicy,
                                  OpsGraphRuntimeStateManager graphStateManager,
                                  OpsGraphTopologyAssembler topologyAssembler,
                                  OpsAnalysisNodeLifecycle lifecycle) {
        this.planner = planner;
        this.planLoopCoordinator = planLoopCoordinator;
        this.routingPolicy = routingPolicy;
        this.graphStateManager = graphStateManager;
        this.topologyAssembler = topologyAssembler;
        this.lifecycle = lifecycle;
    }

    void execute(OpsAnalysisNodeExecutionContext context) {
        boolean feedbackLoopEnabled = topologyAssembler.feedbackLoopEnabled(context.definition());
        if (!feedbackLoopEnabled && context.analysisState().reflectionInvocations().incrementAndGet() > 1) {
            finish(context, "SKIPPED", "主 Agent 复盘循环已执行过，本次跳过重复触发。");
            return;
        }

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planLoopCoordinator.ensurePlan(
                context.request(), context.response(), context.questionContext(), context.planRef());
        planLoopCoordinator.waitForInitialSubAgents(plan, context.initialResults(), context.request());
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> snapshot = graphStateManager.analysisResultSnapshot(
                context.initialResults(), context.graphState(), routingPolicy);
        context.response().setInvestigationResults(snapshot);
        context.resultRef().set(snapshot);
        String reviewMode = routingPolicy.reviewMode(context.definition(), context.node());
        if (OpsAnalysisRoutingPolicy.REVIEW_MODE_NONE.equals(reviewMode)) {
            context.output().put("results", snapshot);
            finish(context, "SKIPPED", "复盘方式为不复盘，保留首轮 observation 并进入报告。");
            return;
        }
        if (feedbackLoopEnabled) {
            executeFeedbackReview(context, snapshot, plan);
            return;
        }

        int maxMainRounds = routingPolicy.maxMainRounds(context.definition(), context.request());
        OpsAnalysisPlanLoopCoordinator.MainLoopOutcome outcome = planLoopCoordinator.runMainPlanExecuteLoop(
                context.definition(),
                context.request(),
                context.response(),
                context.questionContext(),
                context.steps(),
                context.planRef(),
                plan,
                snapshot,
                maxMainRounds,
                OpsAnalysisRoutingPolicy.REVIEW_MODE_IMMEDIATE.equals(reviewMode)
                        && !context.immediateFollowUpSources().isEmpty());
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results = outcome.results();
        context.resultRef().set(results);
        context.response().setInvestigationResults(results);
        String summary = "主 Agent 已完成 Plan-and-Execute 复盘循环，执行轮次："
                + outcome.executedRounds() + "/" + maxMainRounds + "，结果数：" + results.size();
        lifecycle.record(context, "SUCCEEDED", summary);
        context.output().put("results", results);
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    private void executeFeedbackReview(
            OpsAnalysisNodeExecutionContext context,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> snapshot,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        if (graphStateManager.completedExplicitSingleSourceInvestigation(
                context.runtimeRequest(), snapshot, routingPolicy)) {
            context.output().put("results", snapshot);
            context.output().put("review_decision", "final_report");
            context.output().put("selectedReviewRoutes", List.of());
            finish(context, "SUCCEEDED", "用户已明确限定单一调查数据源且真实查询已完成，保留现有证据并进入最终报告。");
            return;
        }
        OpsGraphTopologyAssembler.LoopRoundStatus loopStatus = topologyAssembler.primaryFeedbackLoopRoundStatus(
                context.definition(), context.request(), context.graphState());
        int maxMainRounds = loopStatus.maxRounds();
        int reviewRound = loopStatus.nextRound();
        if (topologyAssembler.allFeedbackLoopsExhausted(
                context.definition(), context.request(), context.graphState())) {
            context.output().put("results", snapshot);
            context.output().put("review_decision", "final_report");
            context.output().put("selectedReviewRoutes", List.of());
            finish(context, "SUCCEEDED", "主 Agent Graph 回边循环达到上限，进入最终报告："
                    + loopStatus.currentRound() + "/" + maxMainRounds);
            return;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO nextPlan = planner.replan(
                context.request(),
                context.questionContext(),
                plan,
                snapshot,
                reviewRound + 1,
                maxMainRounds,
                topologyAssembler.routingChoices(
                        context.definition(), "review_decision", context.graphState(), context.request()),
                analysisNodeSkills(context.definition(), context.node()));
        nextPlan = filterGraphReplanTasks(
                nextPlan,
                snapshot,
                context.definition(),
                context.request(),
                context.runtimeRequest(),
                context.graphState());
        List<String> routes = routingPolicy.selectedRoutes(nextPlan);
        if (nextPlan == null || routes.isEmpty()) {
            context.output().put("results", snapshot);
            context.output().put("review_decision", "final_report");
            context.output().put("selectedReviewRoutes", List.of());
            finish(context, "SUCCEEDED", "主 Agent Graph 复盘判断证据无需继续补查，进入最终报告："
                    + reviewRound + "/" + maxMainRounds);
            return;
        }
        context.planRef().set(nextPlan);
        context.response().setInvestigationPlan(nextPlan);
        context.resultRef().set(snapshot);
        context.response().setInvestigationResults(snapshot);
        String reviewDecision = routingPolicy.reviewDecisionForRoutes(routes);
        String summary = "主 Agent Graph 复盘选择补查：" + reviewDecision
                + "，回边轮次：" + loopStatus.currentRound() + "/" + maxMainRounds;
        lifecycle.record(context, "SUCCEEDED", summary);
        context.output().put("plan", nextPlan);
        context.output().put("results", snapshot);
        context.output().put("response", context.response());
        context.output().put("review_decision", reviewDecision);
        context.output().put("selectedReviewRoutes", routes);
        context.output().put("output", summary);
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filterGraphReplanTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OverAllState state) {
        return filterGraphReplanTasks(plan, existingResults, definition, request, null, state);
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filterGraphReplanTasks(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults,
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAgentChatRequest runtimeRequest,
            OverAllState state) {
        if (plan == null) {
            return null;
        }
        Set<String> allowedByIntent = routingPolicy.routeConstraint(
                runtimeRequest, "allowedInvestigationSources");
        Set<String> excludedByIntent = routingPolicy.routeConstraint(
                runtimeRequest, "excludedCapabilities");
        boolean singleExplicitSource = allowedByIntent.size() == 1;
        Set<String> executedSources = routingPolicy.executedSources(existingResults);
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks = Optional.ofNullable(plan.getTasks())
                .orElse(List.of()).stream()
                .filter(task -> task != null && StringUtils.hasText(task.getSource()))
                .map(routingPolicy::canonicalTask)
                .filter(task -> routingPolicy.allowsSource(
                        task.getSource(), allowedByIntent, excludedByIntent))
                .filter(task -> !executedSources.contains(task.getSource())
                        || (!singleExplicitSource
                        && topologyAssembler.hasAvailableFeedbackEdgeForSource(
                        definition, request, state, task.getSource())))
                .sorted(Comparator.comparing(task -> Optional.ofNullable(task.getPriority()).orElse(99)))
                .toList();
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent(plan.getIntent())
                .reason(plan.getReason())
                .changeRequested(plan.getChangeRequested())
                .changeIntent(plan.getChangeIntent())
                .tasks(tasks)
                .conditionalTasks(Optional.ofNullable(plan.getConditionalTasks()).orElse(List.of()))
                .skippedTasks(Optional.ofNullable(plan.getSkippedTasks()).orElse(List.of()))
                .build();
    }

    private void finish(OpsAnalysisNodeExecutionContext context, String status, String summary) {
        lifecycle.record(context, status, summary);
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    private List<String> analysisNodeSkills(OpsAgentDefinition definition, OpsWorkflowNode node) {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        if (definition != null && definition.getSkills() != null) {
            skills.addAll(definition.getSkills());
        }
        if (node != null && node.getSkills() != null) {
            skills.addAll(node.getSkills());
        }
        return new ArrayList<>(skills);
    }
}
