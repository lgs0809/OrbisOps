package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemoryRuntimeInjectionService;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Assembles authoritative memory selection and runtime context-bundle metadata. */
final class OpsWorkSessionMemoryContextAssembler {

    private final OpsConversationMemoryService memoryService;
    private final OpsMemoryRuntimeInjectionService memoryRuntimeInjectionService;
    private final OpsWorkSessionContextBundleCoordinator bundleCoordinator;

    OpsWorkSessionMemoryContextAssembler(
            OpsConversationMemoryService memoryService,
            OpsMemoryRuntimeInjectionService memoryRuntimeInjectionService,
            OpsWorkSessionContextBundleCoordinator bundleCoordinator) {
        this.memoryService = memoryService;
        this.memoryRuntimeInjectionService = memoryRuntimeInjectionService;
        this.bundleCoordinator = bundleCoordinator;
    }

    String assemble(OpsAgentChatRequest request,
                    boolean memoryEnabled,
                    List<OpsRuntimeEvent> events,
                    Consumer<OpsRuntimeEvent> eventSink,
                    long requestStartedNanos) {
        if (!memoryEnabled) {
            Map<String, Object> metadata = baseMetadata(request);
            metadata.putIfAbsent("scene", trustedLanding(request)
                    ? "APPROVED_LANDING_WORK_SESSION"
                    : "PRE_APPROVAL_WORK_SESSION");
            bundleCoordinator.create(
                    request,
                    "",
                    metadata,
                    events,
                    eventSink,
                    requestStartedNanos);
            return "";
        }

        long memoryStartedNanos = System.nanoTime();
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MEMORY_CONTEXT_STARTED")
                .status("RUNNING")
                .summary("开始装配会话热记忆、压缩记忆和长期记忆。")
                .payload(payloadWithElapsed(
                        Map.of("sessionId", value(request.getSessionId())),
                        requestStartedNanos))
                .build());

        Map<String, Object> metadata = baseMetadata(request);
        Object taskType = metadata.get("taskType");
        metadata.putIfAbsent(
                "scene",
                isOpsAnalysisRequest(request)
                        ? "OPS_TROUBLESHOOTING"
                        : taskType == null ? "" : String.valueOf(taskType).trim());

        OpsMemorySelection conversation = memoryService.assembleSelection(
                request.getSessionId(),
                request.getUserId(),
                request.getQuery(),
                metadata);
        String context = conversation.context();
        OpsMemorySelection explicit = memoryRuntimeInjectionService == null
                ? new OpsMemorySelection("", List.of())
                : memoryRuntimeInjectionService.select(request);
        if (StringUtils.hasText(explicit.context())) {
            context = StringUtils.hasText(context)
                    ? context + "\n\n" + explicit.context()
                    : explicit.context();
        }
        List<Map<String, Object>> refs = new ArrayList<>(conversation.refs());
        refs.addAll(explicit.refs());
        metadata.put(
                "_authoritativeMemorySelection",
                new OpsMemorySelection(context, refs));

        bundleCoordinator.create(
                request,
                context,
                metadata,
                events,
                eventSink,
                requestStartedNanos);
        if (StringUtils.hasText(context)) {
            record(events, eventSink, OpsRuntimeEvent.of(
                    "MEMORY_CONTEXT",
                    "SUCCEEDED",
                    "已装配会话热/冷记忆，供主 Agent 问题重写和后续推理使用。"));
        }
        record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("MEMORY_CONTEXT_FINISHED")
                .status("SUCCEEDED")
                .summary(StringUtils.hasText(context)
                        ? "会话记忆装配完成。"
                        : "没有可注入的会话记忆。")
                .payload(payloadWithElapsed(Map.of(
                        "durationMs", elapsedMs(memoryStartedNanos),
                        "memoryContextChars", context.length()),
                        requestStartedNanos))
                .build());
        return context;
    }

    private boolean trustedLanding(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return false;
        Object raw = request.getMetadata().get(OpsAgentRunExecutionContextFactory.AUTHORITATIVE_KEY);
        return raw instanceof AgentRunExecutionContext context
                && context.stage() == AgentExecutionStage.LANDING
                && context.approvedPackage().isPresent();
    }

    private Map<String, Object> baseMetadata(OpsAgentChatRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                request.getMetadata() == null ? Map.of() : request.getMetadata());
        metadata.putIfAbsent("projectId", value(request.getProjectId()));
        metadata.putIfAbsent("agentId", value(request.getAgentDefinitionId()));
        return metadata;
    }

    private boolean isOpsAnalysisRequest(OpsAgentChatRequest request) {
        return request != null
                && request.getMetadata() != null
                && request.getMetadata().containsKey(
                WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST);
    }

    private void record(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> sink,
                        OpsRuntimeEvent event) {
        events.add(event);
        if (sink != null) sink.accept(event);
    }

    private Map<String, Object> payloadWithElapsed(
            Map<String, Object> seed,
            long startedNanos) {
        Map<String, Object> payload = new LinkedHashMap<>(
                seed == null ? Map.of() : seed);
        payload.put("elapsedMs", elapsedMs(startedNanos));
        return payload;
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
