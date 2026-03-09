package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Owns Work Session question preparation and durable conversation memory projection. */
final class OpsRuntimeConversationContextCoordinator {

    private final OpsWorkSessionContextPreparationService contextPreparationService;
    private final OpsConversationMemoryService memoryService;
    private final OpsRuntimeEventJournal eventJournal;

    OpsRuntimeConversationContextCoordinator(
            OpsWorkSessionContextPreparationService contextPreparationService,
            OpsConversationMemoryService memoryService,
            OpsRuntimeEventJournal eventJournal) {
        this.contextPreparationService = contextPreparationService;
        this.memoryService = memoryService;
        this.eventJournal = eventJournal;
    }

    void prepareMainQuestion(OpsAgentDefinition definition,
                             OpsAgentChatRequest request,
                             OpsRuntimeExecutionPlan plan,
                             List<OpsRuntimeEvent> events,
                             Consumer<OpsRuntimeEvent> eventSink,
                             long requestStartedNanos) {
        contextPreparationService.prepare(
                definition,
                request,
                plan,
                events,
                eventSink,
                requestStartedNanos);
    }

    String memoryContext(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) {
            return "";
        }
        Object value = request.getMetadata().get(
                OpsWorkSessionContextMetadataKeys.RUNTIME_MEMORY_CONTEXT);
        return value == null ? "" : String.valueOf(value);
    }

    boolean hasMemoryContext(OpsAgentChatRequest request) {
        return request != null
                && request.getMetadata() != null
                && request.getMetadata().containsKey(
                OpsWorkSessionContextMetadataKeys.RUNTIME_MEMORY_CONTEXT);
    }

    String originalUserQuery(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) {
            return "";
        }
        Object original = request.getMetadata().get(
                OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY);
        String originalText = original == null ? "" : String.valueOf(original);
        return StringUtils.hasText(originalText)
                ? originalText
                : value(request.getQuery());
    }

    String userMemoryContent(OpsAgentChatRequest request) {
        String original = originalUserQuery(request);
        String rewritten = request == null ? "" : value(request.getQuery());
        if (StringUtils.hasText(original)
                && StringUtils.hasText(rewritten)
                && !original.equals(rewritten)) {
            return "原始问题：" + original + "\n主 Agent 重写问题：" + rewritten;
        }
        return StringUtils.hasText(original) ? original : rewritten;
    }

    void appendMessage(OpsAgentChatRequest request,
                       OpsAgentDefinition definition,
                       List<OpsRuntimeEvent> events,
                       Consumer<OpsRuntimeEvent> eventSink,
                       long requestStartedNanos,
                       String role,
                       String content,
                       Map<String, Object> metadata) {
        try {
            memoryService.appendMessage(
                    request.getSessionId(),
                    request.getUserId(),
                    role,
                    value(content),
                    metadata == null ? Map.of() : metadata);
        } catch (RuntimeException error) {
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("MEMORY_APPEND_DEGRADED")
                    .status("DEGRADED")
                    .summary("Memory 写入失败，主流程降级继续：" + value(error.getMessage()))
                    .payload(eventJournal.payloadWithElapsed(Map.of(
                            "agentId", definition == null ? "" : value(definition.getAgentId()),
                            "role", value(role),
                            "error", value(error.getMessage())), requestStartedNanos))
                    .build());
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
