package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns Graph node guards, lifecycle order and output projection. */
final class OpsGraphNodeLifecycle {

    private final OpsGraphRuntimeStateManager graphRuntimeStateManager;
    private final OpsGraphTopologyAssembler graphTopologyAssembler;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsAnalysisRoutingPolicy analysisRoutingPolicy;
    private final OpsGraphNodeLifecycleReporter reporter;

    OpsGraphNodeLifecycle(OpsGraphRuntimeStateManager graphRuntimeStateManager,
                          OpsGraphTopologyAssembler graphTopologyAssembler,
                          OpsAnalysisRuntimeStateManager analysisStateManager,
                          OpsAnalysisRoutingPolicy analysisRoutingPolicy,
                          OpsGraphNodeLifecycleReporter reporter) {
        this.graphRuntimeStateManager = graphRuntimeStateManager;
        this.graphTopologyAssembler = graphTopologyAssembler;
        this.analysisStateManager = analysisStateManager;
        this.analysisRoutingPolicy = analysisRoutingPolicy;
        this.reporter = reporter;
    }

    Map<String, Object> execute(OpsAgentDefinition definition,
                                OpsWorkflowNode node,
                                OpsAgentChatRequest request,
                                OverAllState state,
                                List<OpsRuntimeEvent> events,
                                Consumer<OpsRuntimeEvent> eventSink,
                                OpsGraphNodeExecutionCoordinator.Hooks hooks,
                                Function<OpsGraphNodeExecutionContext, OpsGraphNodeExecutionResult> nodeBody) {
        hooks.assertNotCanceled(request);
        String nodeType = hooks.executionNodeType(node);
        if (graphRuntimeStateManager.stateBoolean(
                state, OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY)
                && !"NOTIFY".equals(nodeType)
                && !"END".equals(nodeType)) {
            return Map.of();
        }
        if ("END".equals(nodeType) && !graphRuntimeStateManager.claimEndExecution(request)) {
            return Map.of(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
        }

        String routeKey = analysisRoutingPolicy.incomingRouteKey(definition, node);
        long startedNanos = System.nanoTime();
        long startedMillis = System.currentTimeMillis();
        String startedAt = analysisStateManager.now();
        OpsAnalysisRuntimeStateManager.State analysisState = analysisStateManager.ensure(definition, request);
        boolean publishGenericLifecycle = analysisState != null
                && !analysisStateManager.supportsNode(nodeType);
        reporter.runtimeEvent(events, eventSink, runtimeEvent(
                "NODE_START", node, nodeType, routeKey, "RUNNING",
                "节点开始执行：" + node.getNodeId(), null));
        reporter.started(analysisState, node, startedAt, publishGenericLifecycle);

        OpsGraphNodeExecutionContext context = new OpsGraphNodeExecutionContext(
                definition, node, request, state, events, eventSink, hooks,
                nodeType, routeKey, analysisState,
                String.valueOf(state.value("output", request.getQuery())),
                startedAt, startedMillis, startedNanos, publishGenericLifecycle);
        try {
            OpsGraphNodeExecutionResult execution = nodeBody.apply(context);
            Map<String, Object> result = execution.result() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(execution.result());
            String output = execution.output();
            if (java.util.Set.of("AGENT", "AGENTSCOPE", "LLM", "CHAT").contains(nodeType)) {
                String normalized = OpsWorkflowNodeOutputContract.compile(node).normalize(output, node.getOutputKey());
                if (!java.util.Objects.equals(output, normalized)) {
                    reporter.runtimeEvent(events, eventSink, OpsRuntimeEvent.builder()
                            .eventType("NODE_OUTPUT_NORMALIZED").nodeId(node.getNodeId()).nodeType(nodeType)
                            .status("SUCCEEDED").summary("已移除与节点输出字段同名的单层包装；内部结果仍需通过原契约校验。")
                            .payload(Map.of("outputKey", node.getOutputKey(),
                                    "originalHash", cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(output),
                                    "normalizedHash", cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(normalized)))
                            .build());
                    output = normalized;
                }
            }
            result.put("output", output);
            result.put(StringUtils.hasText(node.getOutputKey())
                    ? node.getOutputKey()
                    : node.getNodeId(), output);
            if (graphTopologyAssembler.marksGraphCompletedOnSuccess(nodeType)) {
                result.put(OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true);
            }
            reporter.runtimeEvent(events, eventSink, runtimeEvent(
                    "NODE_END", node, nodeType, routeKey, "SUCCEEDED",
                    "节点执行完成：" + node.getNodeId(), output));
            reporter.telemetry(definition, nodeType, "succeeded", startedNanos);
            reporter.finished(
                    analysisState, node, output, startedAt, startedMillis, publishGenericLifecycle);
            return result;
        } catch (RuntimeException error) {
            reporter.telemetry(definition, nodeType, "failed", startedNanos);
            reporter.runtimeEvent(events, eventSink, runtimeEvent(
                    "NODE_FAILED", node, nodeType, routeKey, "FAILED",
                    "节点执行失败：" + node.getNodeId() + "，" + error.getMessage(), null));
            reporter.failed(
                    analysisState, node, error, startedAt, startedMillis, publishGenericLifecycle);
            throw error;
        }
    }

    private OpsRuntimeEvent runtimeEvent(String eventType,
                                         OpsWorkflowNode node,
                                         String nodeType,
                                         String routeKey,
                                         String status,
                                         String summary,
                                         String content) {
        return OpsRuntimeEvent.builder()
                .eventType(eventType)
                .nodeId(node.getNodeId())
                .nodeType(nodeType)
                .agent(node.getAgent())
                .source(routeKey)
                .status(status)
                .summary(summary)
                .content(content)
                .build();
    }
}
