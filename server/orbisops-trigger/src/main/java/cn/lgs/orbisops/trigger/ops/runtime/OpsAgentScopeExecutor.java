package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Orchestrates one AgentScope/ReAct execution across bounded runtime components. */
@Slf4j
final class OpsAgentScopeExecutor {

    private final OpsAgentScopeConfigPolicy configPolicy;
    private final OpsAgentScopeContextPreparer contextPreparer;
    private final OpsAgentScopePipelineFactory pipelineFactory;
    private final OpsAgentScopeOutputReader outputReader;
    private final OpsAgentScopeOutcomeEnvelope outcomeEnvelope;

    OpsAgentScopeExecutor(OpsRuntimeResourceAssembler resourceAssembler,
                          OpsNodeRagService nodeRagService,
                          OpsRuntimePromptAssembler promptAssembler,
                          Executor executor) {
        this(resourceAssembler, nodeRagService, promptAssembler, executor, null, 240);
    }

    OpsAgentScopeExecutor(OpsRuntimeResourceAssembler resourceAssembler,
                          OpsNodeRagService nodeRagService,
                          OpsRuntimePromptAssembler promptAssembler,
                          Executor executor,
                          ExecutorService modelCallExecutor,
                          int modelCallTimeoutSeconds) {
        this.configPolicy = new OpsAgentScopeConfigPolicy();
        OpsAgentScopeFlowPolicy flowPolicy = new OpsAgentScopeFlowPolicy();
        this.contextPreparer = new OpsAgentScopeContextPreparer(
                resourceAssembler, nodeRagService);
        this.pipelineFactory = new OpsAgentScopePipelineFactory(
                promptAssembler,
                executor,
                configPolicy,
                flowPolicy,
                modelCallExecutor,
                modelCallTimeoutSeconds);
        this.outputReader = new OpsAgentScopeOutputReader();
        this.outcomeEnvelope = new OpsAgentScopeOutcomeEnvelope();
    }

    String execute(OpsAgentDefinition definition,
                   OpsAgentChatRequest request,
                   String input,
                   List<OpsRuntimeEvent> events,
                   Consumer<OpsRuntimeEvent> eventSink,
                   Hooks hooks) {
        requireHooks(hooks);
        hooks.assertNotCanceled(request);
        List<OpsAgentScopeConfig> configs = configPolicy.configs(
                definition, request);
        if (configs.isEmpty()) {
            configs = List.of(configPolicy.defaultConfig(definition));
        }
        OpsAgentScopeContextPreparer.Prepared prepared = contextPreparer.prepare(
                definition,
                request,
                input,
                configs,
                events,
                eventSink,
                hooks);
        OpsAgentScopePipelineFactory.Prepared pipeline = pipelineFactory.prepare(
                definition,
                request,
                configs,
                prepared.bundles(),
                events,
                eventSink,
                hooks);
        try {
            Agent flow = pipelineFactory.buildFlow(definition, pipeline);
            record(events, eventSink, OpsRuntimeEvent.of(
                    "REACT_STARTED",
                    "RUNNING",
                    "ReAct 工具流 "
                            + pipeline.mode()
                            + " 开始执行："
                            + pipeline.name()));
            hooks.assertNotCanceled(request);
            int eventOffset = eventSize(events);
            int toolCallsBefore = toolCallStartedCount(events);
            Optional<OverAllState> state = invokeFlow(flow, prepared.content(), request.getSessionId());
            String rawOutput = rawOutput(state, pipeline.outputKey());
            if (OpsAgentOutputGuard.isTransportErrorEnvelope(rawOutput)
                    && toolCallStartedCount(events) == toolCallsBefore) {
                record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("REACT_TRANSPORT_RETRY")
                        .nodeType("AGENTSCOPE_RETRY")
                        .agent(pipeline.name())
                        .status("RETRYING")
                        .summary("ReAct 首轮模型传输失败且未执行 Tool，执行一次无副作用重试。")
                        .build());
                hooks.assertNotCanceled(request);
                state = invokeFlow(
                        flow,
                        prepared.content(),
                        request.getSessionId() + "-transport-retry");
                rawOutput = rawOutput(state, pipeline.outputKey());
            }
            boolean evidenceBackedDegradation = false;
            if (OpsAgentOutputGuard.isTransportErrorEnvelope(rawOutput)
                    && hasEvidenceSince(events, eventOffset)) {
                evidenceBackedDegradation = true;
                rawOutput = evidenceBackedDegradedOutput(request, events, eventOffset);
                recordEvidenceBackedWorkflowOutcome(request, events, eventSink, pipeline.name(), eventOffset);
            }
            OpsAgentScopeOutcomeEnvelope.Parsed parsedOutcome = outcomeEnvelope.parse(rawOutput);
            if (parsedOutcome.present()) {
                Map<String, Object> outcome = reconcileActionClassification(
                        parsedOutcome.outcome(), request, events, eventSink, pipeline.name());
                record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("REACT_OUTCOME")
                        .nodeType("AGENTSCOPE_OUTCOME")
                        .agent(pipeline.name())
                        .status("SUCCEEDED")
                        .summary("ReAct 最终结构化 outcome 已解析。")
                        .payload(outcome)
                        .build());
            } else if (outputReader.isMeaningfulText(rawOutput) && !evidenceBackedDegradation) {
                record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("REACT_OUTCOME_MISSING")
                        .nodeType("AGENTSCOPE_OUTCOME")
                        .agent(pipeline.name())
                        .status("DEGRADED")
                        .summary("ReAct 最终回答缺少结构化 outcome，保留兼容降级。")
                        .build());
            }
            String output = parsedOutcome.visibleOutput();
            if (!outputReader.isMeaningfulText(output)) {
                log.warn(
                        "ReAct 执行完成但未生成有效输出，outputKey={}，stateValues={}，diagnostics={}",
                        pipeline.outputKey(),
                        state.map(outputReader::stateValueTypes).orElse(Map.of()),
                        state.map(value -> outputReader.stateDiagnostics(
                                value, pipeline.outputKey())).orElse(Map.of()));
            }
            record(events, eventSink, OpsRuntimeEvent.of(
                    "REACT_FINISHED",
                    "SUCCEEDED",
                    "ReAct 工具流 " + pipeline.mode() + " 执行完成。"));
            return output;
        } catch (Exception error) {
            record(events, eventSink, OpsRuntimeEvent.of(
                    "REACT_FAILED",
                    "FAILED",
                    "ReAct 工具流执行失败：" + error.getMessage()));
            throw new IllegalStateException(
                    "ReAct 工具流执行失败：" + error.getMessage(), error);
        }
    }

    List<OpsAgentScopeConfig> agentScopeConfigs(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request) {
        return configPolicy.configs(definition, request);
    }

    String stateOutput(OverAllState state, String outputKey) {
        return outputReader.stateOutput(state, outputKey);
    }

    boolean isMeaningfulText(String text) {
        return outputReader.isMeaningfulText(text);
    }

    int maxToolRounds(OpsAgentDefinition definition,
                      OpsAgentScopeConfig config,
                      OpsAgentChatRequest request) {
        return configPolicy.maxToolRounds(definition, config, request);
    }

    private Optional<OverAllState> invokeFlow(Agent flow, String content, String threadId) throws Exception {
        return flow.invoke(
                content,
                RunnableConfig.builder()
                        .threadId(threadId)
                        .build());
    }

    private String rawOutput(Optional<OverAllState> state, String outputKey) {
        return state
                .map(value -> outputReader.stateOutput(value, outputKey))
                .filter(outputReader::isMeaningfulText)
                .orElse("");
    }

    private int eventSize(List<OpsRuntimeEvent> events) {
        if (events == null) return 0;
        synchronized (events) {
            return events.size();
        }
    }

    private int toolCallStartedCount(List<OpsRuntimeEvent> events) {
        return (int) eventsSince(events, 0).stream()
                .filter(event -> event != null && "TOOL_CALL_STARTED".equals(event.getEventType()))
                .count();
    }

    private List<OpsRuntimeEvent> eventsSince(List<OpsRuntimeEvent> events, int offset) {
        if (events == null || events.isEmpty()) return List.of();
        synchronized (events) {
            int safeOffset = Math.max(0, Math.min(offset, events.size()));
            return List.copyOf(events.subList(safeOffset, events.size()));
        }
    }

    private boolean hasEvidenceSince(List<OpsRuntimeEvent> events, int offset) {
        return eventsSince(events, offset).stream().anyMatch(event -> {
            if (event == null) return false;
            String type = event.getEventType() == null ? "" : event.getEventType();
            String status = event.getStatus() == null ? "" : event.getStatus();
            if ("SOURCE_QUERY_FINISHED".equals(type)) {
                Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
                return "SUCCEEDED".equalsIgnoreCase(status) && Boolean.TRUE.equals(payload.get("verified"));
            }
            return "RAG_RETRIEVE".equals(type)
                    && ("SUCCEEDED".equalsIgnoreCase(status) || "NOT_FOUND".equalsIgnoreCase(status));
        });
    }

    private Set<String> evidenceSourceTypesSince(List<OpsRuntimeEvent> events, int offset) {
        Set<String> result = new LinkedHashSet<>();
        for (OpsRuntimeEvent event : eventsSince(events, offset)) {
            if (event == null) continue;
            String type = event.getEventType() == null ? "" : event.getEventType();
            String status = event.getStatus() == null ? "" : event.getStatus();
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            if ("SOURCE_QUERY_FINISHED".equals(type)
                    && "SUCCEEDED".equalsIgnoreCase(status)
                    && Boolean.TRUE.equals(payload.get("verified"))) {
                String sourceType = String.valueOf(payload.getOrDefault("sourceType", "")).trim().toUpperCase(Locale.ROOT);
                if (!sourceType.isBlank()) result.add(sourceType);
            } else if ("RAG_RETRIEVE".equals(type)
                    && ("SUCCEEDED".equalsIgnoreCase(status) || "NOT_FOUND".equalsIgnoreCase(status))) {
                result.add("RAG");
            }
        }
        return Set.copyOf(result);
    }

    private String evidenceBackedDegradedOutput(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            int eventOffset) {
        Set<String> sourceTypes = evidenceSourceTypesSince(events, eventOffset);
        return "已完成本轮权威证据采集（sourceTypes=" + sourceTypes
                + "），但最终模型综合阶段发生传输故障。为避免基于未完成的综合过程生成结论，"
                + "本次仅保留已验证证据并将结论标记为证据不足；未执行任何额外生产变更。";
    }

    private void recordEvidenceBackedWorkflowOutcome(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName,
            int eventOffset) {
        Set<String> sourceTypes = evidenceSourceTypesSince(events, eventOffset);
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("REACT_EVIDENCE_BACKED_DEGRADATION")
                .nodeType("AGENTSCOPE_OUTCOME")
                .agent(agentName)
                .status("DEGRADED")
                .summary("ReAct 最终模型综合失败，已保留本轮已验证证据并降级输出。")
                .payload(Map.of("authoritativeSourceTypes", List.copyOf(sourceTypes)))
                .build());
        String semantic = semanticClassification(request);
        if (semantic.isBlank() || "ABSTAIN".equals(semantic)) return;
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("actionClassification", semantic);
        outcome.put("requiresAction", "REMEDIATE".equals(semantic));
        outcome.put("verificationStatus", "VERIFY".equals(semantic) ? "INSUFFICIENT" : "NOT_APPLICABLE");
        outcome.put("abstained", false);
        outcome.put("evidenceCompleteness", "INSUFFICIENT");
        outcome.put("source", "EVIDENCE_BACKED_DEGRADATION");
        outcome.put("authoritativeSourceTypes", List.copyOf(sourceTypes));
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("WORKFLOW_OUTCOME")
                .nodeType("WORKFLOW_OUTCOME")
                .agent(agentName)
                .status("SUCCEEDED")
                .summary("ReAct 综合失败后，已由权威证据保全路径补齐结构化终态。")
                .payload(Map.copyOf(outcome))
                .build());
    }

    private String semanticClassification(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return "";
        Object semanticValue = request.getMetadata().get(
                OpsWorkSessionContextMetadataKeys.TASK_ACTION_CLASSIFICATION);
        String semantic = semanticValue == null
                ? ""
                : String.valueOf(semanticValue).trim().toUpperCase(Locale.ROOT);
        return List.of("DIAGNOSE", "REMEDIATE", "VERIFY", "ABSTAIN").contains(semantic)
                ? semantic
                : "";
    }

    private Map<String, Object> reconcileActionClassification(
            Map<String, Object> modelOutcome,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String agentName) {
        if (modelOutcome == null || modelOutcome.isEmpty()
                || request == null || request.getMetadata() == null) {
            return modelOutcome == null ? Map.of() : modelOutcome;
        }
        Object semanticValue = request.getMetadata().get(
                OpsWorkSessionContextMetadataKeys.TASK_ACTION_CLASSIFICATION);
        String semantic = semanticValue == null
                ? ""
                : String.valueOf(semanticValue).trim().toUpperCase(Locale.ROOT);
        if (!List.of("DIAGNOSE", "REMEDIATE", "VERIFY", "ABSTAIN").contains(semantic)) {
            return modelOutcome;
        }
        String model = String.valueOf(modelOutcome.getOrDefault("actionClassification", ""));
        if (semantic.equals(model)) return modelOutcome;

        Map<String, Object> reconciled = new LinkedHashMap<>(modelOutcome);
        reconciled.put("actionClassification", semantic);
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("ACTION_CLASSIFICATION_RECONCILED")
                .nodeType("AGENTSCOPE_OUTCOME")
                .agent(agentName)
                .status("SUCCEEDED")
                .summary("最终 outcome 已按用户请求语义分类进行一致性对齐。")
                .payload(Map.of(
                        "modelClassification", model,
                        "semanticClassification", semantic,
                        "source", "MAIN_QUERY_SEMANTICS"))
                .build());
        return Map.copyOf(reconciled);
    }

    private void record(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> eventSink,
                        OpsRuntimeEvent event) {
        events.add(event);
        if (eventSink != null) eventSink.accept(event);
    }

    private void requireHooks(Hooks hooks) {
        if (hooks == null) {
            throw new IllegalArgumentException("AGENT_SCOPE_HOOKS_REQUIRED");
        }
    }

    interface Hooks {
        void assertNotCanceled(OpsAgentChatRequest request);

        boolean hasPreparedMemoryContext(OpsAgentChatRequest request);

        String memoryContext(OpsAgentChatRequest request);

        ToolLoopCoordinator toolLoopCoordinator();
    }
}
