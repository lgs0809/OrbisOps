package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Resolves AgentScope resources and prepares RAG/memory-enriched input. */
final class OpsAgentScopeContextPreparer {

    private final OpsRuntimeResourceAssembler resourceAssembler;
    private final OpsNodeRagService nodeRagService;

    OpsAgentScopeContextPreparer(OpsRuntimeResourceAssembler resourceAssembler,
                                 OpsNodeRagService nodeRagService) {
        this.resourceAssembler = resourceAssembler;
        this.nodeRagService = nodeRagService;
    }

    Prepared prepare(OpsAgentDefinition definition,
                     OpsAgentChatRequest request,
                     String input,
                     List<OpsAgentScopeConfig> configs,
                     List<OpsRuntimeEvent> events,
                     Consumer<OpsRuntimeEvent> eventSink,
                     OpsAgentScopeExecutor.Hooks hooks) {
        List<OpsRuntimeResourceBundle> bundles = new ArrayList<>();
        for (OpsAgentScopeConfig config : configs) {
            bundles.add(resourceAssembler.assembleAgentScope(
                    definition, config, request, events, eventSink));
        }

        // AgentScope is the ReAct runtime: knowledge retrieval must be an explicit
        // model-selected tool action, not an unconditional pre-flight side effect.
        // Pre-fetching here made every RAG-enabled project look as if RAG had been
        // routed even for pure Prometheus/ES questions and consumed model capacity
        // before the agent had made a routing decision.
        String content = input;
        if (!hooks.hasPreparedMemoryContext(request)) {
            throw new IllegalStateException("WORK_SESSION_CONTEXT_NOT_PREPARED");
        }
        String memoryContext = hooks.memoryContext(request);
        if (StringUtils.hasText(memoryContext)) {
            content = "### 会话记忆\n"
                    + memoryContext
                    + "\n\n### 当前输入\n"
                    + content;
        }
        return new Prepared(List.copyOf(bundles), content);
    }

    private String knowledgeBaseScope(OpsRuntimeResourceBundle bundle) {
        if (bundle == null || bundle.getMetadata() == null) return "";
        return String.valueOf(
                bundle.getMetadata().getOrDefault("knowledgeBaseScope", ""));
    }

    record Prepared(List<OpsRuntimeResourceBundle> bundles, String content) {
    }
}
