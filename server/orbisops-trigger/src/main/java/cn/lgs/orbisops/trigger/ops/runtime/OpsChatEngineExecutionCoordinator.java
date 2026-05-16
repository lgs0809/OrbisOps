package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Executes the top-level CHAT engine with governed resources, Memory, RAG and model invocation. */
final class OpsChatEngineExecutionCoordinator {

    private final OpsRuntimeResourceAssembler resourceAssembler;
    private final OpsNodeRagService nodeRagService;
    private final OpsRuntimePromptAssembler promptAssembler;
    private final OpsRuntimeLlmInvoker llmInvoker;
    private final OpsRuntimeConversationContextCoordinator conversationContextCoordinator;
    private final OpsRuntimeEventJournal eventJournal;

    OpsChatEngineExecutionCoordinator(
            OpsRuntimeResourceAssembler resourceAssembler,
            OpsNodeRagService nodeRagService,
            OpsRuntimePromptAssembler promptAssembler,
            OpsRuntimeLlmInvoker llmInvoker,
            OpsRuntimeConversationContextCoordinator conversationContextCoordinator,
            OpsRuntimeEventJournal eventJournal) {
        this.resourceAssembler = resourceAssembler;
        this.nodeRagService = nodeRagService;
        this.promptAssembler = promptAssembler;
        this.llmInvoker = llmInvoker;
        this.conversationContextCoordinator = conversationContextCoordinator;
        this.eventJournal = eventJournal;
    }

    String execute(OpsAgentDefinition definition,
                   OpsAgentChatRequest request,
                   List<OpsRuntimeEvent> events,
                   Consumer<OpsRuntimeEvent> eventSink,
                   boolean memoryEnabled,
                   Hooks hooks) {
        hooks.assertNotCanceled(request);
        long requestStartedNanos = eventJournal.requestStartedNanos(request);
        long resourceStartedNanos = System.nanoTime();
        eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("RESOURCE_ASSEMBLY_STARTED")
                .status("RUNNING")
                .summary("开始装配模型、MCP 工具、Skill 和知识库运行资源。")
                .payload(eventJournal.payloadWithElapsed(
                        Map.of("agentId", value(definition.getAgentId())),
                        requestStartedNanos))
                .build());

        OpsRuntimeResourceBundle bundle = resourceAssembler.assembleAgent(
                definition, request, events, eventSink);
        Map<String, Object> resourcePayload = llmInvoker.runtimeResourcePayload(bundle);
        resourcePayload.put("agentId", value(definition.getAgentId()));
        resourcePayload.put("durationMs", eventJournal.elapsedMs(resourceStartedNanos));
        eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("RESOURCE_ASSEMBLY_FINISHED")
                .status("SUCCEEDED")
                .summary("运行资源装配完成。")
                .payload(eventJournal.payloadWithElapsed(resourcePayload, requestStartedNanos))
                .build());

        String memoryContext = "";
        if (memoryEnabled) {
            if (!conversationContextCoordinator.hasMemoryContext(request)) {
                throw new IllegalStateException("WORK_SESSION_CONTEXT_NOT_PREPARED");
            }
            memoryContext = conversationContextCoordinator.memoryContext(request);
        }
        String userPrompt = nodeRagService.enhancePrompt(
                request.getQuery(),
                request.getQuery(),
                bundle.getRagEnabled(),
                bundle.getKnowledgeBaseId(),
                events,
                eventSink,
                eventSink != null,
                bundle.getProjectId(),
                knowledgeBaseScope(bundle));
        if (StringUtils.hasText(memoryContext)) {
            userPrompt = "### 会话记忆\n" + memoryContext + "\n\n### 当前问题\n" + userPrompt;
        }

        eventJournal.record(events, eventSink,
                OpsRuntimeEvent.of("CHAT_STARTED", "RUNNING", "CHAT 模式开始调用模型。"));
        hooks.assertNotCanceled(request);
        String systemPrompt = promptAssembler.systemPrompt(definition, null, bundle);
        if (eventSink != null) {
            return llmInvoker.callStreaming(
                    systemPrompt,
                    userPrompt,
                    bundle,
                    events,
                    eventSink,
                    requestStartedNanos);
        }
        return llmInvoker.call(
                systemPrompt,
                userPrompt,
                bundle,
                events,
                eventSink,
                requestStartedNanos);
    }

    String knowledgeBaseScope(OpsRuntimeResourceBundle bundle) {
        if (bundle == null || bundle.getMetadata() == null) {
            return "";
        }
        return String.valueOf(bundle.getMetadata().getOrDefault("knowledgeBaseScope", ""));
    }

    interface Hooks {
        void assertNotCanceled(OpsAgentChatRequest request);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
