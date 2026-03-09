package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Delegates Graph node lifecycle and body execution to bounded components. */
final class OpsGraphNodeExecutionCoordinator {

    private final OpsGraphNodeLifecycle lifecycle;
    private final OpsGraphNodeBodyExecutor bodyExecutor;

    OpsGraphNodeExecutionCoordinator(OpsGraphNodeLifecycle lifecycle,
                                     OpsGraphNodeBodyExecutor bodyExecutor) {
        this.lifecycle = lifecycle;
        this.bodyExecutor = bodyExecutor;
    }

    Map<String, Object> execute(OpsAgentDefinition definition,
                                OpsWorkflowNode node,
                                OpsAgentChatRequest request,
                                OverAllState state,
                                List<OpsRuntimeEvent> events,
                                Consumer<OpsRuntimeEvent> eventSink,
                                Hooks hooks) {
        return lifecycle.execute(
                definition,
                node,
                request,
                state,
                events,
                eventSink,
                hooks,
                bodyExecutor::execute);
    }

    String buildAgentScopeNodeInput(OpsAgentDefinition definition,
                                    OpsWorkflowNode node,
                                    OpsAgentChatRequest request,
                                    String input,
                                    OverAllState state,
                                    Hooks hooks) {
        return bodyExecutor.buildAgentScopeNodeInput(
                definition, node, request, input, state, hooks);
    }

    Map<String, Object> createAgentScopeObservationResult(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            String output) {
        return bodyExecutor.createAgentScopeObservationResult(definition, node, output);
    }

    boolean graphReviewDecisionIsFinalReport(OverAllState state) {
        return bodyExecutor.graphReviewDecisionIsFinalReport(state);
    }

    interface Hooks {
        void assertNotCanceled(OpsAgentChatRequest request);

        String executionNodeType(OpsWorkflowNode node);

        OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks();

        long requestStartedNanos(OpsAgentChatRequest request);
    }
}
