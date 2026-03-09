package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;

import java.util.Map;

/** Publishes node lifecycle events and records canonical Analysis steps. */
final class OpsAnalysisNodeLifecycle {

    private final OpsAnalysisRuntimeStateManager stateManager;
    private final GraphEventApplicationService graphEventService;

    OpsAnalysisNodeLifecycle(OpsAnalysisRuntimeStateManager stateManager,
                             GraphEventApplicationService graphEventService) {
        this.stateManager = stateManager;
        this.graphEventService = graphEventService;
    }

    String now() {
        return stateManager.now();
    }

    void assertNotCanceled(OpsAnalysisNodeExecutionContext context) {
        stateManager.assertNotCanceled(context.request());
    }

    void publishStarted(OpsAnalysisNodeExecutionContext context) {
        if (graphEventService == null) {
            return;
        }
        graphEventService.publish(
                context.request().getRunId(),
                context.response().getAnalysisId(),
                "NODE_STARTED",
                context.node(),
                "RUNNING",
                "节点开始执行",
                context.startedAt(),
                null,
                null,
                Map.of("question", context.questionContext().originalQuestion(), "runtime", "generic"));
    }

    void record(OpsAnalysisNodeExecutionContext context, String status, String summary) {
        stateManager.recordStep(
                context.steps(),
                context.request(),
                context.response(),
                context.node(),
                context.nodeType(),
                status,
                summary,
                context.startedAt(),
                context.startedMillis());
    }

    void recordFailure(OpsAnalysisNodeExecutionContext context, RuntimeException error) {
        record(context, "FAILED", error.getMessage());
    }
}
