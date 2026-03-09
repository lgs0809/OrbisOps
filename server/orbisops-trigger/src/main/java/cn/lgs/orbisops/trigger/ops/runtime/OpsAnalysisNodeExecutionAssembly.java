package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsAnalysisReportComposer;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelNotificationService;

/** Composition root for the Analysis node protocol object graph. */
final class OpsAnalysisNodeExecutionAssembly {

    private OpsAnalysisNodeExecutionAssembly() {
    }

    static OpsAnalysisNodeExecutionCoordinator create(
            OpsMainAgentPlanner planner,
            OpsInvestigationExecutor investigationExecutor,
            OpsAnalysisReportComposer reportComposer,
            OpsChannelNotificationService notificationService,
            OpsAnalysisRuntimeStateManager stateManager,
            OpsAnalysisPlanLoopCoordinator planLoopCoordinator,
            OpsAnalysisRoutingPolicy routingPolicy,
            OpsGraphRuntimeStateManager graphStateManager,
            OpsGraphTopologyAssembler topologyAssembler,
            GraphEventApplicationService graphEventService) {
        OpsAnalysisNodeLifecycle lifecycle = new OpsAnalysisNodeLifecycle(
                stateManager, graphEventService);
        OpsAnalysisPlanningNodeExecutor planningExecutor = new OpsAnalysisPlanningNodeExecutor(
                planner,
                investigationExecutor,
                planLoopCoordinator,
                routingPolicy,
                graphStateManager,
                topologyAssembler,
                lifecycle);
        OpsAnalysisInvestigationNodeExecutor graphInvestigationExecutor =
                new OpsAnalysisInvestigationNodeExecutor(
                        investigationExecutor,
                        stateManager,
                        planLoopCoordinator,
                        routingPolicy,
                        graphStateManager,
                        topologyAssembler,
                        lifecycle);
        OpsAnalysisReviewNodeExecutor reviewExecutor = new OpsAnalysisReviewNodeExecutor(
                planner,
                planLoopCoordinator,
                routingPolicy,
                graphStateManager,
                topologyAssembler,
                lifecycle);
        OpsAnalysisTerminalNodeExecutor terminalExecutor = new OpsAnalysisTerminalNodeExecutor(
                reportComposer,
                notificationService,
                stateManager,
                graphStateManager,
                lifecycle);
        return new OpsAnalysisNodeExecutionCoordinator(
                planningExecutor,
                graphInvestigationExecutor,
                reviewExecutor,
                terminalExecutor,
                lifecycle,
                routingPolicy);
    }
}
