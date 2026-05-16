package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.List;
import java.util.function.Consumer;

/** Canonical input shared by Graph node execution boundaries. */
record OpsGraphNodeExecutionContext(
        OpsAgentDefinition definition,
        OpsWorkflowNode node,
        OpsAgentChatRequest request,
        OverAllState state,
        List<OpsRuntimeEvent> events,
        Consumer<OpsRuntimeEvent> eventSink,
        OpsGraphNodeExecutionCoordinator.Hooks hooks,
        String nodeType,
        String routeKey,
        OpsAnalysisRuntimeStateManager.State analysisState,
        String input,
        String startedAt,
        long startedMillis,
        long startedNanos,
        boolean publishGenericNodeLifecycle) {
}
