package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Executes structural, planning, routing and whole-plan Analysis nodes. */
final class OpsAnalysisPlanningNodeExecutor {

    private final OpsMainAgentPlanner planner;
    private final OpsInvestigationExecutor investigationExecutor;
    private final OpsAnalysisPlanLoopCoordinator planLoopCoordinator;
    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphRuntimeStateManager graphStateManager;
    private final OpsGraphTopologyAssembler topologyAssembler;
    private final OpsAnalysisNodeLifecycle lifecycle;

    OpsAnalysisPlanningNodeExecutor(OpsMainAgentPlanner planner,
                                    OpsInvestigationExecutor investigationExecutor,
                                    OpsAnalysisPlanLoopCoordinator planLoopCoordinator,
                                    OpsAnalysisRoutingPolicy routingPolicy,
                                    OpsGraphRuntimeStateManager graphStateManager,
                                    OpsGraphTopologyAssembler topologyAssembler,
                                    OpsAnalysisNodeLifecycle lifecycle) {
        this.planner = planner;
        this.investigationExecutor = investigationExecutor;
        this.planLoopCoordinator = planLoopCoordinator;
        this.routingPolicy = routingPolicy;
        this.graphStateManager = graphStateManager;
        this.topologyAssembler = topologyAssembler;
        this.lifecycle = lifecycle;
    }

    void executeStructural(OpsAnalysisNodeExecutionContext context) {
        String summary = "结构节点透传：" + context.node().getNodeId();
        lifecycle.record(context, "SUCCEEDED", summary);
        context.output().put("response", context.response());
        context.output().put("output", summary);
        if ("END".equals(context.nodeType())) {
            context.output().put(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
        }
    }

    void executePlan(OpsAnalysisNodeExecutionContext context) {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planner.plan(
                context.request(),
                context.questionContext(),
                topologyAssembler.routingChoices(
                        context.definition(), "plan", context.graphState(), context.request()),
                analysisNodeSkills(context.definition(), context.node()));
        context.planRef().set(plan);
        context.response().setInvestigationPlan(plan);
        String summary = "主 Agent 已完成计划：" + value(plan.getIntent());
        lifecycle.record(context, "SUCCEEDED", summary);
        context.output().put("plan", plan);
        context.output().put("selectedRoutes", routingPolicy.selectedRoutes(plan));
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    void executeRouter(OpsAnalysisNodeExecutionContext context) {
        if (graphStateManager.stateBoolean(
                context.graphState(), OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY)) {
            String summary = "Graph 已完成，Router 不再选择后续分支。";
            lifecycle.record(context, "SUCCEEDED", summary);
            context.output().put("selectedRoutes", List.of());
            if (StringUtils.hasText(context.node().getOutputKey())) {
                context.output().put(context.node().getOutputKey(), List.of());
            }
            context.output().put(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
            context.output().put("response", context.response());
            context.output().put("output", summary);
            return;
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = context.planRef().get();
        List<String> routes = routingPolicy.selectedRoutesForRouter(
                context.definition(),
                context.node(),
                context.graphState(),
                plan,
                context.runtimeRequest());
        String summary = "Router 已按已有 Graph State/结构化输出选择分支："
                + (routes.isEmpty() ? "无" : String.join(",", routes));
        lifecycle.record(context, "SUCCEEDED", summary);
        if (plan != null) {
            context.output().put("plan", plan);
        }
        context.output().put("selectedRoutes", routes);
        if (StringUtils.hasText(context.node().getOutputKey())) {
            context.output().put(context.node().getOutputKey(), routes);
        }
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    void executePlanTasks(OpsAnalysisNodeExecutionContext context) {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = context.planRef().get();
        if (plan == null) {
            plan = planner.plan(context.request(), context.questionContext());
            context.planRef().set(plan);
            context.response().setInvestigationPlan(plan);
        }
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results = investigationExecutor.execute(
                context.request(), context.response(), plan, context.questionContext());
        // REVIEW waits on initialResults rather than resultRef.  EXECUTE is the
        // whole-plan variant of the investigation protocol, so its completed
        // batch must become the authoritative initial observation set just as
        // graph-scoped SUB_AGENT nodes do.  Without this hand-off REVIEW waits
        // the full node budget for work that already completed.
        synchronized (context.initialResults()) {
            context.initialResults().clear();
            context.initialResults().addAll(results);
        }
        context.resultRef().set(results);
        context.response().setInvestigationResults(results);
        String summary = "子 Agent 调查完成，结果数：" + results.size();
        lifecycle.record(context, "SUCCEEDED", summary);
        context.output().put("results", results);
        context.output().put(OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY, !results.isEmpty());
        graphStateManager.markInvestigationExecuted(context.runtimeRequest(), !results.isEmpty());
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

    private String value(String value) {
        return value == null ? "" : value;
    }
}
