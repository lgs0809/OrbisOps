package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsTelemetryService;

/** Composition root for Graph node lifecycle and body execution boundaries. */
final class OpsGraphNodeExecutionAssembly {

    private OpsGraphNodeExecutionAssembly() {
    }

    static OpsGraphNodeExecutionCoordinator create(
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsNodeRagService nodeRagService,
            OpsRuntimePromptAssembler promptAssembler,
            OpsRuntimeLlmInvoker llmInvoker,
            OpsAgentScopeExecutionCoordinator agentScopeExecutionCoordinator,
            OpsGraphRuntimeStateManager graphRuntimeStateManager,
            OpsGraphTopologyAssembler graphTopologyAssembler,
            OpsAnalysisRuntimeStateManager analysisStateManager,
            OpsAnalysisNodeExecutionCoordinator analysisNodeExecutionCoordinator,
            OpsAnalysisRoutingPolicy analysisRoutingPolicy,
            OpsGraphConditionEvaluator conditionEvaluator,
            OpsSubWorkflowNodeExecutor subWorkflowNodeExecutor,
            OpsTelemetryService telemetryService,
            GraphEventApplicationService graphEventService,
            OpsWorkflowChangeContextReader changeContextReader) {
        OpsGraphNodePromptContextPolicy promptContextPolicy =
                new OpsGraphNodePromptContextPolicy(
                        analysisRoutingPolicy,
                        graphTopologyAssembler,
                        conditionEvaluator);
        OpsGraphGenericLlmNodeExecutor genericLlmNodeExecutor =
                new OpsGraphGenericLlmNodeExecutor(
                        resourceAssembler,
                        nodeRagService,
                        promptAssembler,
                        llmInvoker,
                        promptContextPolicy);
        OpsGraphAgentScopeNodeExecutor agentScopeNodeExecutor =
                new OpsGraphAgentScopeNodeExecutor(
                        agentScopeExecutionCoordinator,
                        promptAssembler,
                        analysisRoutingPolicy,
                        graphRuntimeStateManager,
                        promptContextPolicy);
        OpsGraphDirectNodeExecutor directNodeExecutor =
                new OpsGraphDirectNodeExecutor(resourceAssembler);
        OpsGraphRouterNodeExecutor routerNodeExecutor =
                new OpsGraphRouterNodeExecutor(java.time.Clock.systemUTC(),changeContextReader);
        OpsGraphNodeBodyExecutor bodyExecutor = new OpsGraphNodeBodyExecutor(
                graphRuntimeStateManager,
                analysisStateManager,
                analysisNodeExecutionCoordinator,
                analysisRoutingPolicy,
                promptContextPolicy,
                genericLlmNodeExecutor,
                agentScopeNodeExecutor,
                directNodeExecutor,
                routerNodeExecutor,
                subWorkflowNodeExecutor);
        OpsGraphNodeLifecycleReporter reporter = new OpsGraphNodeLifecycleReporter(
                analysisStateManager,
                telemetryService,
                graphEventService);
        OpsGraphNodeLifecycle lifecycle = new OpsGraphNodeLifecycle(
                graphRuntimeStateManager,
                graphTopologyAssembler,
                analysisStateManager,
                analysisRoutingPolicy,
                reporter);
        return new OpsGraphNodeExecutionCoordinator(lifecycle, bodyExecutor);
    }
}
