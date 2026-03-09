package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Controlled cutover shell around the legacy StateGraph scheduler.
 * SHADOW persists typed plans and transitions without changing legacy output;
 * GUARDED additionally fails closed when typed lifecycle invariants are violated.
 */
final class OpsTypedWorkflowExecutionCoordinator {

    private static final String CONTEXT_BUNDLE_ID = "contextBundleId";
    private static final String CONTEXT_BUNDLE_HASH = "contextBundleHash";

    private final OpsAgentDefinitionValidator definitionValidator;
    private final OpsRuntimeWorkflowBindingAdapter bindingAdapter;
    private final OpsDurableWorkflowRuntimeCoordinator durableRuntime;
    private final OpsRuntimeResourceAssembler resourceAssembler;
    private final OpsRuntimeContextBundleAdapter contextBundles;
    private final OpsTypedWorkflowSettings settings;
    private final OpsRuntimeEventJournal eventJournal;
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    OpsTypedWorkflowExecutionCoordinator(
            OpsAgentDefinitionValidator definitionValidator,
            OpsRuntimeWorkflowBindingAdapter bindingAdapter,
            OpsDurableWorkflowRuntimeCoordinator durableRuntime,
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsRuntimeContextBundleAdapter contextBundles,
            OpsTypedWorkflowSettings settings,
            OpsRuntimeEventJournal eventJournal) {
        if (definitionValidator == null
                || bindingAdapter == null
                || durableRuntime == null
                || resourceAssembler == null
                || contextBundles == null
                || settings == null
                || eventJournal == null) {
            throw new IllegalArgumentException("TYPED_WORKFLOW_COORDINATOR_DEPENDENCY_REQUIRED");
        }
        this.definitionValidator = definitionValidator;
        this.bindingAdapter = bindingAdapter;
        this.durableRuntime = durableRuntime;
        this.resourceAssembler = resourceAssembler;
        this.contextBundles = contextBundles;
        this.settings = settings;
        this.eventJournal = eventJournal;
    }

    void begin(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            List<OpsWorkflowNode> graphNodes,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        if (!settings.mode().enabled()) return;
        if (definition == null || request == null) {
            handleFailure(request, events, eventSink, "TYPED_WORKFLOW_BEGIN_INPUT_REQUIRED", null);
            return;
        }
        if (!settings.shouldActivate(request)) {
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("TYPED_WORKFLOW_SKIPPED")
                    .status("SKIPPED")
                    .summary("普通短时 Agent Graph 由 StateGraph 独立执行，不创建 Durable Workflow 双状态。")
                    .payload(Map.of(
                            "mode", settings.mode().name(),
                            "scope", settings.scope().name(),
                            "reasonCode", "NOT_DURABLE_EXECUTION"))
                    .build());
            return;
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("TYPED_WORKFLOW_SKIPPED")
                    .status("SKIPPED")
                    .summary("Typed Workflow 未接管无显式节点的兼容 Chat Graph。")
                    .payload(Map.of("mode", settings.mode().name(), "reasonCode", "NO_EXPLICIT_WORKFLOW_NODES"))
                    .build());
            return;
        }
        try {
            CompiledAgentDefinitionVersion compiled = definitionValidator.compile(definition);
            RuntimeContextBundleSnapshot contextBundle = contextBundles.requireSnapshot(
                    metadata(request, CONTEXT_BUNDLE_ID),
                    metadata(request, CONTEXT_BUNDLE_HASH));
            OpsRuntimeResourceBundle runtime = resourceAssembler.assembleAgent(
                    definition, request, events, eventSink);
            Map<String, OpsRuntimeResourceBundle> nodeRuntimes = assembleNodeRuntimes(
                    compiled, graphNodes, definition, request, events, eventSink);
            BoundWorkflowExecutionPlan plan = bindingAdapter.bind(
                    compiled,
                    runtime,
                    nodeRuntimes,
                    contextBundle,
                    request.getSessionId(),
                    request.getRunId(),
                    Instant.now());
            boolean recovered = resumed(request);
            DurableWorkflowRunState state = recovered
                    ? durableRuntime.recover(request, plan)
                    : durableRuntime.start(request, plan, Map.of(
                            "query", value(request.getQuery()),
                            "legacyGraphNodeCount", graphNodes == null ? 0 : graphNodes.size(),
                            "cutoverMode", settings.mode().name()));
            activeRuns.put(request.getRunId(), new ActiveRun(
                    plan,
                    settings.mode(),
                    recovered,
                    new ConcurrentHashMap<>(),
                    nodeAttemptLimits(definition)));
            request.getMetadata().put("typedWorkflowMode", settings.mode().name());
            request.getMetadata().put("typedWorkflowPlanHash", plan.planHash());
            request.getMetadata().put("typedWorkflowDefinitionHash", plan.definitionHash());
            request.getMetadata().put("typedWorkflowRecovered", recovered);
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType(recovered
                            ? "TYPED_WORKFLOW_RECOVERED"
                            : "TYPED_WORKFLOW_BOUND")
                    .status("SUCCEEDED")
                    .summary(recovered
                            ? "Typed Workflow 已校验 Bound Plan 与 durable checkpoint，并进入恢复执行。"
                            : "Typed Workflow 已完成结构编译、资源绑定并创建 durable 起始 checkpoint。")
                    .payload(Map.of(
                            "mode", settings.mode().name(),
                            "planHash", plan.planHash(),
                            "definitionHash", plan.definitionHash(),
                            "contextBundleHash", plan.contextBundleHash(),
                            "nodeCount", plan.nodes().size(),
                            "routeCount", plan.routes().size(),
                            "recovered", recovered,
                            "recoveredStatus", state.status().name()))
                    .build());
        } catch (RuntimeException error) {
            handleFailure(request, events, eventSink, "TYPED_WORKFLOW_BIND_FAILED", error);
        }
    }

    private Map<String, OpsRuntimeResourceBundle> assembleNodeRuntimes(
            CompiledAgentDefinitionVersion compiled,
            List<OpsWorkflowNode> graphNodes,
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        Map<String, OpsWorkflowNode> sourceNodes = new LinkedHashMap<>();
        if (graphNodes != null) {
            for (OpsWorkflowNode node : graphNodes) {
                if (node != null && node.getNodeId() != null && !node.getNodeId().isBlank()) {
                    sourceNodes.put(node.getNodeId(), node);
                }
            }
        }
        Map<String, OpsRuntimeResourceBundle> resolved = new LinkedHashMap<>();
        for (var compiledNode : compiled.nodes()) {
            if (compiledNode == null || compiledNode.resources() == null || compiledNode.resources().isEmpty()) {
                continue;
            }
            OpsWorkflowNode source = sourceNodes.get(compiledNode.nodeId());
            if (source == null) {
                throw new IllegalStateException("TYPED_WORKFLOW_SOURCE_NODE_REQUIRED:" + compiledNode.nodeId());
            }
            resolved.put(compiledNode.nodeId(), resourceAssembler.assembleNode(
                    definition, source, request, events, eventSink));
        }
        return Map.copyOf(resolved);
    }

    Optional<Map<String, Object>> replayCompletedNode(
            OpsAgentChatRequest request,
            OpsWorkflowNode node,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null || !run.recovered()) return Optional.empty();
        try {
            String nodeId = requiredNodeId(node);
            DurableWorkflowRunState state = durableRuntime.state(request);
            int attempt = run.replayCursors().merge(nodeId, 1, Integer::sum);
            Object storedOutput = state.variables().get(nodeAttemptOutputKey(nodeId, attempt));
            String expectedHash = value(state.variables().get(nodeAttemptOutputHashKey(nodeId, attempt)));
            if (storedOutput == null
                    && attempt == state.node(nodeId).attempt()
                    && state.node(nodeId).status() == DurableWorkflowNodeStatus.SUCCEEDED) {
                // Backward-compatible recovery for checkpoints written before attempt-scoped outputs.
                storedOutput = state.variables().get(nodeOutputKey(nodeId));
                expectedHash = state.node(nodeId).outputHash();
            }
            if (storedOutput == null) return Optional.empty();
            Map<String, Object> output = outputMap(storedOutput);
            String outputHash = CanonicalObjectHasher.sha256(output);
            if (expectedHash.isBlank() || !outputHash.equals(expectedHash)) {
                throw new IllegalStateException(
                        "TYPED_WORKFLOW_RECOVERED_OUTPUT_HASH_MISMATCH:" + nodeId + ":" + attempt);
            }
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("TYPED_WORKFLOW_NODE_REPLAYED")
                    .status("SUCCEEDED")
                    .nodeId(nodeId)
                    .nodeType(node == null ? "" : value(node.getType()))
                    .summary("恢复执行已回放完成节点的 durable 输出，未重复调用节点主体。")
                    .payload(Map.of(
                            "planHash", run.plan().planHash(),
                            "outputHash", outputHash,
                            "attempt", attempt))
                    .build());
            return Optional.of(output);
        } catch (RuntimeException error) {
            deactivateOnFailure(
                    request,
                    events,
                    eventSink,
                    "TYPED_WORKFLOW_NODE_REPLAY_FAILED",
                    error);
            return Optional.empty();
        }
    }

    void beforeNode(
            OpsAgentChatRequest request,
            OpsWorkflowNode node,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        try {
            String nodeId = requiredNodeId(node);
            durableRuntime.beforeNode(request, nodeId, maxAttempts(run, nodeId));
        } catch (RuntimeException error) {
            deactivateOnFailure(request, events, eventSink, "TYPED_WORKFLOW_NODE_BEFORE_FAILED", error);
        }
    }

    void afterNode(
            OpsAgentChatRequest request,
            OpsWorkflowNode node,
            Map<String, Object> output,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        try {
            String nodeId = requiredNodeId(node);
            Map<String, Object> durableOutput = output == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(output));
            String outputHash = CanonicalObjectHasher.sha256(durableOutput);
            int attempt = durableRuntime.state(request).node(nodeId).attempt();
            Map<String, Object> variableUpdates = new LinkedHashMap<>();
            variableUpdates.put("lastCompletedNodeId", nodeId);
            variableUpdates.put("lastOutputHash", outputHash);
            variableUpdates.put(nodeOutputKey(nodeId), durableOutput);
            variableUpdates.put(nodeAttemptOutputKey(nodeId, attempt), durableOutput);
            variableUpdates.put(nodeAttemptOutputHashKey(nodeId, attempt), outputHash);
            durableRuntime.afterNode(
                    request,
                    nodeId,
                    outputHash,
                    variableUpdates);
        } catch (RuntimeException error) {
            deactivateOnFailure(request, events, eventSink, "TYPED_WORKFLOW_NODE_AFTER_FAILED", error);
        }
    }

    void nodeFailed(
            OpsAgentChatRequest request,
            OpsWorkflowNode node,
            RuntimeException failure,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        try {
            String nodeId = requiredNodeId(node);
            durableRuntime.failNode(
                    request,
                    nodeId,
                    "LEGACY_GRAPH_NODE_FAILED",
                    failure == null ? "" : value(failure.getMessage()),
                    false,
                    maxAttempts(run, nodeId));
        } catch (RuntimeException typedFailure) {
            deactivateOnFailure(
                    request,
                    events,
                    eventSink,
                    "TYPED_WORKFLOW_NODE_FAILURE_CHECKPOINT_FAILED",
                    typedFailure);
        }
    }

    void complete(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        try {
            durableRuntime.complete(request);
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("TYPED_WORKFLOW_COMPLETED")
                    .status("SUCCEEDED")
                    .summary("Typed Workflow durable 生命周期与旧 Graph 终态一致完成。")
                    .payload(Map.of(
                            "mode", run.mode().name(),
                            "planHash", run.plan().planHash()))
                    .build());
        } catch (RuntimeException error) {
            deactivateOnFailure(request, events, eventSink, "TYPED_WORKFLOW_COMPLETE_FAILED", error);
        }
    }

    void cancel(
            OpsAgentChatRequest request,
            String reason,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        try {
            durableRuntime.cancel(request, reason);
        } catch (RuntimeException error) {
            deactivateOnFailure(request, events, eventSink, "TYPED_WORKFLOW_CANCEL_FAILED", error);
        }
    }

    void graphFailed(
            OpsAgentChatRequest request,
            RuntimeException error,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null) return;
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("TYPED_WORKFLOW_GRAPH_FAILED")
                .status("FAILED")
                .summary("旧 Graph 失败，Typed Workflow 保留最后 durable checkpoint 供恢复判定。")
                .payload(Map.of(
                        "mode", run.mode().name(),
                        "planHash", run.plan().planHash(),
                        "error", error == null ? "" : value(error.getMessage())))
                .build());
    }

    void cleanup(OpsAgentChatRequest request) {
        if (request == null || request.getRunId() == null) return;
        activeRuns.remove(request.getRunId());
        durableRuntime.cleanup(request);
    }

    private void deactivateOnFailure(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String reasonCode,
            RuntimeException error) {
        if (request != null && request.getRunId() != null) activeRuns.remove(request.getRunId());
        handleFailure(request, events, eventSink, reasonCode, error);
    }

    private void handleFailure(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String reasonCode,
            RuntimeException error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", settings.mode().name());
        payload.put("reasonCode", reasonCode);
        payload.put("error", error == null ? "" : value(error.getMessage()));
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("TYPED_WORKFLOW_FAILED")
                .status("FAILED")
                .summary("Typed Workflow 生命周期校验失败：" + reasonCode)
                .payload(Map.copyOf(payload))
                .build());
        if (settings.mode().failClosed()) {
            throw new IllegalStateException(reasonCode, error);
        }
    }

    private ActiveRun active(OpsAgentChatRequest request) {
        if (request == null || request.getRunId() == null) return null;
        return activeRuns.get(request.getRunId());
    }

    private void record(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            OpsRuntimeEvent event) {
        eventJournal.record(events, eventSink, event);
    }

    private String metadata(OpsAgentChatRequest request, String key) {
        Object value = request == null || request.getMetadata() == null
                ? null
                : request.getMetadata().get(key);
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("TYPED_WORKFLOW_METADATA_REQUIRED:" + key);
        return normalized;
    }

    private String requiredNodeId(OpsWorkflowNode node) {
        String nodeId = node == null ? "" : value(node.getNodeId());
        if (nodeId.isBlank()) throw new IllegalArgumentException("TYPED_WORKFLOW_NODE_ID_REQUIRED");
        return nodeId;
    }

    private boolean resumed(OpsAgentChatRequest request) {
        return request != null
                && request.getMetadata() != null
                && !value(request.getMetadata().get("resumedFromAttemptId")).isBlank();
    }

    private String nodeOutputKey(String nodeId) {
        return "nodeOutput:" + nodeId;
    }

    private String nodeAttemptOutputKey(String nodeId, int attempt) {
        return "nodeOutput:" + nodeId + ":" + attempt;
    }

    private String nodeAttemptOutputHashKey(String nodeId, int attempt) {
        return "nodeOutputHash:" + nodeId + ":" + attempt;
    }

    private Map<String, Integer> nodeAttemptLimits(OpsAgentDefinition definition) {
        Map<String, Integer> limits = new LinkedHashMap<>();
        if (definition != null && definition.getLoops() != null) {
            for (OpsLoopPolicy loop : definition.getLoops()) {
                if (loop == null || loop.getNodes() == null) continue;
                int rounds = loop.getMaxRounds() == null ? 1 : Math.max(1, loop.getMaxRounds());
                int loopAttempts = Math.min(21, rounds + 1);
                for (String nodeId : loop.getNodes()) {
                    if (nodeId == null || nodeId.isBlank()) continue;
                    limits.merge(nodeId.trim(), loopAttempts, Math::max);
                }
            }
        }
        return Map.copyOf(limits);
    }

    private int maxAttempts(ActiveRun run, String nodeId) {
        if (run == null || nodeId == null) return settings.maxNodeAttempts();
        return Math.max(settings.maxNodeAttempts(), run.nodeAttemptLimits().getOrDefault(nodeId, 1));
    }

    private Map<String, Object> outputMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalStateException("TYPED_WORKFLOW_RECOVERED_OUTPUT_MISSING");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Collections.unmodifiableMap(result);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ActiveRun(
            BoundWorkflowExecutionPlan plan,
            OpsTypedWorkflowSettings.Mode mode,
            boolean recovered,
            Map<String, Integer> replayCursors,
            Map<String, Integer> nodeAttemptLimits) {
    }
}
