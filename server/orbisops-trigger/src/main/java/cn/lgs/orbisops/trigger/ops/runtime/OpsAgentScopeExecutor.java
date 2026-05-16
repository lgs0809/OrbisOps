package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.ToolLoopCoordinator;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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
    private final OpsAgentScopeChangePackageFallback changePackageFallback;
    OpsAgentScopeExecutor(OpsRuntimeResourceAssembler resourceAssembler,
                          OpsNodeRagService nodeRagService,
                          OpsRuntimePromptAssembler promptAssembler,
                          Executor executor) {
        this(resourceAssembler, nodeRagService, promptAssembler, executor, null, 240, OpsMcpDiscoveryPolicy.defaults());
    }

    OpsAgentScopeExecutor(OpsRuntimeResourceAssembler resourceAssembler,
                          OpsNodeRagService nodeRagService,
                          OpsRuntimePromptAssembler promptAssembler,
                          Executor executor,
                          ExecutorService modelCallExecutor,
                          int modelCallTimeoutSeconds, OpsMcpDiscoveryPolicy discoveryPolicy) {
        this.configPolicy = new OpsAgentScopeConfigPolicy();
        OpsAgentScopeFlowPolicy flowPolicy = new OpsAgentScopeFlowPolicy();
        this.contextPreparer = new OpsAgentScopeContextPreparer(
                resourceAssembler, nodeRagService);
        this.pipelineFactory = new OpsAgentScopePipelineFactory(promptAssembler, executor,
                configPolicy, flowPolicy, modelCallExecutor, modelCallTimeoutSeconds, discoveryPolicy);
        this.outputReader = new OpsAgentScopeOutputReader();
        this.outcomeEnvelope = new OpsAgentScopeOutcomeEnvelope();
        this.changePackageFallback = new OpsAgentScopeChangePackageFallback(outputReader);
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
            boolean streamVisibleAnswer = pipeline.agents().size() == 1;
            Optional<OverAllState> state;
            try {
                state = invokeFlow(
                        flow,
                        prepared.content(),
                        request.getSessionId(),
                        events,
                        eventSink,
                        streamVisibleAnswer);
            } catch (RuntimeException error) {
                Optional<String> fallback = changePackageFallback.recoverFinalAnswerFailure(
                        error, events, eventOffset, eventSink, pipeline.name());
                if (fallback.isEmpty()) throw error;
                record(events, eventSink, OpsRuntimeEvent.of(
                        "REACT_FINISHED",
                        "SUCCEEDED",
                        "ReAct 工具流 " + pipeline.mode() + " 执行完成。"));
                return fallback.get();
            }
            String rawOutput = rawOutput(state, pipeline.outputKey());
            rawOutput = changePackageFallback.recoverEmptyOutput(
                    rawOutput, events, eventOffset, eventSink, pipeline.name()).orElse(rawOutput);
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
                        request.getSessionId() + "-transport-retry",
                        events,
                        eventSink,
                        streamVisibleAnswer);
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
                Map<String, Object> outcome = parsedOutcome.outcome();
                record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("REACT_OUTCOME")
                        .nodeType("AGENTSCOPE_OUTCOME")
                        .agent(pipeline.name())
                        .status("SUCCEEDED")
                        .summary("ReAct 最终结构化 outcome 已解析。")
                        .payload(outcome)
                        .build());
            } else if (outputReader.isMeaningfulText(rawOutput) && !evidenceBackedDegradation
                    && !(configs.size() == 1 && OpsRuntimePromptAssembler.hasJsonOutputContract(configs.get(0).getOutputContract()))) {
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
            Optional<OpsModelProviderFailureClassifier.Failure> providerFailure =
                    OpsModelProviderFailureClassifier.classify(error);
            if (providerFailure.isPresent()) {
                OpsModelProviderFailureClassifier.Failure failure = providerFailure.get();
                record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("REACT_FAILED")
                        .status("FAILED")
                        .summary(failure.eventSummary())
                        .payload(Map.of("reasonCode", failure.code()))
                        .build());
                throw new IllegalStateException(failure.code(), error);
            }
            record(events, eventSink, OpsRuntimeEvent.of(
                    "REACT_FAILED",
                    "FAILED",
                    "ReAct 工具流执行失败，请查看运行详情。"));
            throw new IllegalStateException("REACT_EXECUTION_FAILED", error);
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

    private Optional<OverAllState> invokeFlow(
            Agent flow,
            String content,
            String threadId,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            boolean streamVisibleAnswer) throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(threadId)
                .build();
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        NodeOutput last = flow.stream(content, config)
                .doOnNext(output -> {
                    if (!streamVisibleAnswer
                            || !(output instanceof StreamingOutput<?> streaming)
                            || streaming.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
                        return;
                    }
                    String chunk = streaming.chunk();
                    if ((chunk == null || chunk.isEmpty()) && streaming.message() != null) {
                        chunk = streaming.message().getText();
                    }
                    for (String delta : projector.accept(chunk)) {
                        emitTextDelta(events, eventSink, delta);
                    }
                })
                .blockLast();
        if (streamVisibleAnswer) {
            for (String delta : projector.finish()) {
                emitTextDelta(events, eventSink, delta);
            }
        }
        return Optional.ofNullable(last).map(NodeOutput::state);
    }

    private void emitTextDelta(
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String delta) {
        if (delta == null || delta.isEmpty()) return;
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("TEXT_DELTA")
                .nodeType("AGENTSCOPE_STREAM")
                .status("RUNNING")
                .summary("ReAct 最终回答输出片段。")
                .content(delta)
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
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("requiresAction", false);
        outcome.put("verificationStatus", "INSUFFICIENT");
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
