package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;

import java.util.ArrayList;

/** Executes one graph-scoped investigation node and its immediate follow-ups. */
final class OpsAnalysisInvestigationNodeExecutor {

    private final OpsInvestigationExecutor investigationExecutor;
    private final OpsAnalysisRuntimeStateManager stateManager;
    private final OpsAnalysisPlanLoopCoordinator planLoopCoordinator;
    private final OpsAnalysisRoutingPolicy routingPolicy;
    private final OpsGraphRuntimeStateManager graphStateManager;
    private final OpsGraphTopologyAssembler topologyAssembler;
    private final OpsAnalysisNodeLifecycle lifecycle;

    OpsAnalysisInvestigationNodeExecutor(OpsInvestigationExecutor investigationExecutor,
                                         OpsAnalysisRuntimeStateManager stateManager,
                                         OpsAnalysisPlanLoopCoordinator planLoopCoordinator,
                                         OpsAnalysisRoutingPolicy routingPolicy,
                                         OpsGraphRuntimeStateManager graphStateManager,
                                         OpsGraphTopologyAssembler topologyAssembler,
                                         OpsAnalysisNodeLifecycle lifecycle) {
        this.investigationExecutor = investigationExecutor;
        this.stateManager = stateManager;
        this.planLoopCoordinator = planLoopCoordinator;
        this.routingPolicy = routingPolicy;
        this.graphStateManager = graphStateManager;
        this.topologyAssembler = topologyAssembler;
        this.lifecycle = lifecycle;
    }

    void execute(OpsAnalysisNodeExecutionContext context,
                 String nodeType,
                 boolean includeLatestObservation) {
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = planLoopCoordinator.ensurePlan(
                context.request(), context.response(), context.questionContext(), context.planRef());
        String source = routingPolicy.resolveSource(context.definition(), context.node(), nodeType);
        OpsAnalysisResponseDTO.InvestigationTaskDTO task = routingPolicy.selectedTask(plan, source)
                .orElseGet(() -> routingPolicy.graphNodeTask(context.node(), nodeType, source));
        task = routingPolicy.enrichWithGraphHandoff(
                task,
                context.definition(),
                context.node(),
                edge -> topologyAssembler.edgeActiveForPrompt(edge, context.graphState()));
        stateManager.assertNotCanceled(context.request());
        OpsAnalysisResponseDTO.InvestigationResultDTO result = investigationExecutor.executeGraphSubAgent(
                task, context.request(), context.response(), context.questionContext());
        synchronized (context.initialResults()) {
            context.initialResults().add(result);
            context.response().setInvestigationResults(new ArrayList<>(context.initialResults()));
        }
        context.resultRef().set(new ArrayList<>(context.initialResults()));
        String summary = task.getAgent() + " 返回 " + value(result.getStatus()) + "：" + value(result.getSummary());
        lifecycle.record(context, value(result.getStatus()), summary);
        if (!topologyAssembler.feedbackLoopEnabled(context.definition())) {
            planLoopCoordinator.triggerImmediateFollowUps(
                    context.definition(),
                    context.request(),
                    context.response(),
                    context.questionContext(),
                    context.steps(),
                    context.runtimeNotes(),
                    plan,
                    context.initialResults(),
                    result,
                    context.immediateFollowUpSources(),
                    context.resultRef());
        }
        if (includeLatestObservation) {
            context.output().put("latestObservation", result);
        }
        context.output().put("results", new ArrayList<>(context.initialResults()));
        context.output().put(OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY, true);
        graphStateManager.markInvestigationExecuted(context.runtimeRequest(), true);
        context.output().put("response", context.response());
        context.output().put("output", summary);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
