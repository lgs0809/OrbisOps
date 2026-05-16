package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Projects Graph node runtime events, Analysis events and telemetry. */
final class OpsGraphNodeLifecycleReporter {

    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsTelemetryService telemetryService;
    private final GraphEventApplicationService graphEventService;

    OpsGraphNodeLifecycleReporter(OpsAnalysisRuntimeStateManager analysisStateManager,
                                  OpsTelemetryService telemetryService,
                                  GraphEventApplicationService graphEventService) {
        this.analysisStateManager = analysisStateManager;
        this.telemetryService = telemetryService;
        this.graphEventService = graphEventService;
    }

    void runtimeEvent(List<OpsRuntimeEvent> events,
                      Consumer<OpsRuntimeEvent> eventSink,
                      OpsRuntimeEvent event) {
        events.add(event);
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    void started(OpsAnalysisRuntimeStateManager.State analysisState,
                 OpsWorkflowNode node,
                 String startedAt,
                 boolean enabled) {
        if (!enabled || graphEventService == null) {
            return;
        }
        graphEventService.publish(
                analysisState.analysisRequest().getRunId(),
                analysisState.response().getAnalysisId(),
                "NODE_STARTED",
                node,
                "RUNNING",
                "节点开始执行",
                startedAt,
                null,
                null,
                Map.of("runtime", "generic"));
    }

    void finished(OpsAnalysisRuntimeStateManager.State analysisState,
                  OpsWorkflowNode node,
                  String output,
                  String startedAt,
                  long startedMillis,
                  boolean enabled) {
        if (!enabled || graphEventService == null) {
            return;
        }
        graphEventService.publish(
                analysisState.analysisRequest().getRunId(),
                analysisState.response().getAnalysisId(),
                "NODE_FINISHED",
                node,
                "SUCCEEDED",
                "节点执行完成",
                startedAt,
                analysisStateManager.now(),
                Math.max(0L, System.currentTimeMillis() - startedMillis),
                Map.of(
                        "runtime", "generic",
                        "content", OpsMemoryTextUtils.abbreviate(output, 4000)));
    }

    void failed(OpsAnalysisRuntimeStateManager.State analysisState,
                OpsWorkflowNode node,
                RuntimeException error,
                String startedAt,
                long startedMillis,
                boolean enabled) {
        if (!enabled || graphEventService == null) {
            return;
        }
        graphEventService.publish(
                analysisState.analysisRequest().getRunId(),
                analysisState.response().getAnalysisId(),
                "NODE_FAILED",
                node,
                "FAILED",
                "节点执行失败：" + error.getMessage(),
                startedAt,
                analysisStateManager.now(),
                Math.max(0L, System.currentTimeMillis() - startedMillis),
                Map.of("runtime", "generic"));
    }

    void telemetry(OpsAgentDefinition definition,
                   String nodeType,
                   String status,
                   long startedNanos) {
        if (telemetryService == null) {
            return;
        }
        telemetryService.recordRuntimeNode(
                definition.getAgentId(),
                definition.getEngine(),
                nodeType,
                status,
                (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
