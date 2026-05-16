package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.domain.agentdefinition.compilation.CompiledAgentDefinitionVersion;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.workflow.model.BoundWorkflowExecutionPlan;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState;
import cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitType;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;

import java.time.Duration;
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
    private final WorkflowApprovalApplicationService workflowApprovals;
    private final OpsWorkflowApprovalChannelBridge workflowApprovalChannelBridge;
    private final OpsTypedWorkflowSettings settings;
    private final OpsRuntimeEventJournal eventJournal;
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    OpsTypedWorkflowExecutionCoordinator(
            OpsAgentDefinitionValidator definitionValidator,
            OpsRuntimeWorkflowBindingAdapter bindingAdapter,
            OpsDurableWorkflowRuntimeCoordinator durableRuntime,
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsRuntimeContextBundleAdapter contextBundles,
            WorkflowApprovalApplicationService workflowApprovals,
            OpsWorkflowApprovalChannelBridge workflowApprovalChannelBridge,
            OpsTypedWorkflowSettings settings,
            OpsRuntimeEventJournal eventJournal) {
        if (definitionValidator == null
                || bindingAdapter == null
                || durableRuntime == null
                || resourceAssembler == null
                || contextBundles == null
                || workflowApprovals == null
                || workflowApprovalChannelBridge == null
                || settings == null
                || eventJournal == null) {
            throw new IllegalArgumentException("TYPED_WORKFLOW_COORDINATOR_DEPENDENCY_REQUIRED");
        }
        this.definitionValidator = definitionValidator;
        this.bindingAdapter = bindingAdapter;
        this.durableRuntime = durableRuntime;
        this.resourceAssembler = resourceAssembler;
        this.contextBundles = contextBundles;
        this.workflowApprovals = workflowApprovals;
        this.workflowApprovalChannelBridge = workflowApprovalChannelBridge;
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
        if ("PLATFORM_LANDING_RUNTIME".equals(definition.getDefinitionKind())) {
            var authority = new OpsAgentRunExecutionContextFactory().resolve(request);
            var fixed = new OpsPlatformLandingRuntimeDefinitionFactory().create(request.getProjectId());
            if (authority == null
                    || authority.stage() != cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage.LANDING
                    || !fixed.getAgentId().equals(definition.getAgentId())
                    || !fixed.getDefinitionHash().equals(definition.getDefinitionHash())) {
                throw new SecurityException("PLATFORM_LANDING_RUNTIME_AUTHORITY_REQUIRED");
            }
            // Landing has its own Work Session lease, operation journal and independent verifier.
            // It is not a user-authored Durable Workflow and must not acquire a second lifecycle.
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("TYPED_WORKFLOW_SKIPPED").status("SKIPPED")
                    .summary("平台 Landing 由已审批的 ReAct Work Session 执行，不进入用户 Workflow 生命周期。")
                    .payload(Map.of("reasonCode", "PLATFORM_LANDING_WORK_SESSION"))
                    .build());
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
                    nodeAttemptLimits(definition),
                    new OpsWorkflowNodeOutputBoundary(plan, definition, definitionValidator, workflowApprovals)));
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
            int cursor = run.replayCursors().getOrDefault(nodeId, 0);
            int lastAttempt = state.node(nodeId).attempt();
            int attempt = cursor + 1;
            Object storedOutput = null;
            String expectedHash = "";
            // Waiting/retried attempts have no completed output. A graph traversal
            // must consume the next completed attempt, not equate the two counters.
            for (; attempt <= lastAttempt; attempt++) {
                storedOutput = state.variables().get(nodeAttemptOutputKey(nodeId, attempt));
                expectedHash = value(state.variables().get(nodeAttemptOutputHashKey(nodeId, attempt)));
                if (storedOutput != null) break;
                if (!expectedHash.isBlank()) {
                    throw new IllegalStateException("TYPED_WORKFLOW_RECOVERED_OUTPUT_MISSING:" + nodeId + ":" + attempt);
                }
            }
            if (storedOutput == null && cursor < lastAttempt
                    && state.node(nodeId).status() == DurableWorkflowNodeStatus.SUCCEEDED) {
                // Backward-compatible recovery for checkpoints written before attempt-scoped outputs.
                attempt = lastAttempt;
                storedOutput = state.variables().get(nodeOutputKey(nodeId));
                expectedHash = state.node(nodeId).outputHash();
            }
            if (storedOutput == null) {
                run.replayCursors().put(nodeId, lastAttempt);
                return Optional.empty();
            }
            Map<String, Object> output = outputMap(storedOutput);
            String outputHash = CanonicalObjectHasher.sha256(output);
            if (expectedHash.isBlank() || !outputHash.equals(expectedHash)) {
                throw new IllegalStateException(
                        "TYPED_WORKFLOW_RECOVERED_OUTPUT_HASH_MISMATCH:" + nodeId + ":" + attempt);
            }
            run.outputBoundary().validate(request, node, output);
            run.replayCursors().put(nodeId, attempt);
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

    Optional<Map<String, Object>> handleHumanApproval(
            OpsAgentChatRequest request,
            OpsWorkflowNode node,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        ActiveRun run = active(request);
        if (run == null || node == null || !"HUMAN_APPROVAL".equalsIgnoreCase(value(node.getType()))) {
            return Optional.empty();
        }
        run.outputBoundary().validateNode(request, node);
        String nodeId = requiredNodeId(node);
        WorkflowApprovalRecord approval = workflowApprovals.find(request.getRunId(), nodeId).orElse(null);
        if (approval == null) {
            durableRuntime.beforeNode(request, nodeId, maxAttempts(run, nodeId));
            String channelId = request.getMetadata() == null ? "" : value(request.getMetadata().get("channelId"));
            String target = request.getMetadata() == null ? "" : value(request.getMetadata().get("externalConversationId"));
            WorkflowApprovalApplicationService.IssuedApproval issued = workflowApprovals.issue(
                    new WorkflowApprovalApplicationService.IssueCommand(
                            request.getRunId(),
                            request.getProjectId(),
                            nodeId,
                            channelId,
                            target,
                            approvalRequestSummary(node),
                            Duration.ofSeconds(approvalTimeoutSeconds(node))));
            try {
                workflowApprovalChannelBridge.sendIfChannel(request, node, issued);
            } catch (RuntimeException deliveryFailure) {
                workflowApprovals.invalidate(issued.record().approvalId());
                throw deliveryFailure;
            }
            durableRuntime.waitFor(
                    request,
                    nodeId,
                    DurableWorkflowWaitType.HUMAN_APPROVAL,
                    issued.record().waitTokenHash(),
                    issued.record().expiresAt());
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("WORKFLOW_APPROVAL_WAITING")
                    .status("WAITING_APPROVAL")
                    .nodeId(nodeId)
                    .nodeType("HUMAN_APPROVAL")
                    .summary("Workflow 已进入 durable human approval wait。")
                    .payload(Map.of(
                            "approvalId", issued.record().approvalId(),
                            "expiresAt", issued.record().expiresAt().toString(),
                            "channelBound", !issued.record().channelId().isBlank()))
                    .build());
            throw new OpsWorkflowApprovalPendingException(nodeId, issued.record().approvalId());
        }

        if (approval.status() == WorkflowApprovalRecord.Status.WAITING) {
            throw new OpsWorkflowApprovalPendingException(nodeId, approval.approvalId());
        }
        if (approval.status() == WorkflowApprovalRecord.Status.EXPIRED) {
            throw new SecurityException("WORKFLOW_APPROVAL_EXPIRED:" + approval.approvalId());
        }

        DurableWorkflowRunState state = durableRuntime.state(request);
        if (state.waitState().active()) {
            durableRuntime.resumeWait(request, approval.waitTokenHash(), approval.status().name());
            state = durableRuntime.state(request);
        }
        if (state.node(nodeId).status() == DurableWorkflowNodeStatus.READY) {
            durableRuntime.beforeNode(request, nodeId, maxAttempts(run, nodeId));
        }
        Map<String, Object> output = Map.of(
                "approvalId", approval.approvalId(),
                "decision", approval.status().name(),
                "approved", approval.status() == WorkflowApprovalRecord.Status.APPROVED,
                "decidedBy", value(approval.decidedBy()));
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("WORKFLOW_APPROVAL_RESUMED")
                .status("SUCCEEDED")
                .nodeId(nodeId)
                .nodeType("HUMAN_APPROVAL")
                .summary("Workflow 已消费 durable human approval decision 并恢复节点执行。")
                .payload(Map.of(
                        "approvalId", approval.approvalId(),
                        "decision", approval.status().name()))
                .build());
        return Optional.of(output);
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
            run.outputBoundary().validateNode(request, node);
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
            run.outputBoundary().validate(request, node, durableOutput);
            String outputHash = CanonicalObjectHasher.sha256(durableOutput);
            int attempt = durableRuntime.state(request).node(nodeId).attempt();
            Map<String, Object> variableUpdates = new LinkedHashMap<>();
            variableUpdates.put("lastCompletedNodeId", nodeId);
            variableUpdates.put("lastOutputHash", outputHash);
            variableUpdates.put(nodeOutputKey(nodeId), durableOutput);
            variableUpdates.put(nodeAttemptOutputKey(nodeId, attempt), durableOutput);
            variableUpdates.put(nodeAttemptOutputHashKey(nodeId, attempt), outputHash);
            variableUpdates.put("nodeValidation:" + nodeId + ":" + attempt, Map.of(
                    "contractVersion", 1, "planHash", run.plan().planHash(),
                    "definitionHash", run.plan().definitionHash(), "outputHash", outputHash));
            durableRuntime.afterNode(
                    request,
                    nodeId,
                    outputHash,
                    variableUpdates);
            // Fresh output was already consumed by this traversal. Do not replay
            // it again if a recovered graph later traverses a feedback edge.
            run.replayCursors().put(nodeId, attempt);
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

    private String approvalRequestSummary(OpsWorkflowNode node) {
        if (node == null) return "Workflow human approval required";
        String description = value(node.getDescription());
        String instruction = value(node.getInstruction());
        StringBuilder summary = new StringBuilder();
        if (!description.isBlank()) summary.append(description);
        if (!instruction.isBlank()) {
            if (!summary.isEmpty()) summary.append("\n");
            summary.append(instruction.length() <= 1200 ? instruction : instruction.substring(0, 1200) + "...");
        }
        return summary.isEmpty() ? "Workflow human approval required" : summary.toString();
    }

    private long approvalTimeoutSeconds(OpsWorkflowNode node) {
        Object configured = node == null || node.getConfig() == null
                ? null
                : node.getConfig().get("timeoutSeconds");
        long seconds = 1800L;
        if (configured instanceof Number number) {
            seconds = number.longValue();
        } else if (configured != null) {
            try {
                seconds = Long.parseLong(String.valueOf(configured).trim());
            } catch (NumberFormatException ignored) {
                seconds = 1800L;
            }
        }
        return Math.max(60L, Math.min(86400L, seconds));
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
            Map<String, Integer> nodeAttemptLimits,
            OpsWorkflowNodeOutputBoundary outputBoundary) {
    }
}
