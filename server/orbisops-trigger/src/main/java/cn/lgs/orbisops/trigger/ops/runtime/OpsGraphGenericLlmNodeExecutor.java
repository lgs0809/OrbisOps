package cn.lgs.orbisops.trigger.ops.runtime;

/** Executes a generic Graph LLM node with resource, RAG and prompt boundaries. */
final class OpsGraphGenericLlmNodeExecutor {

    private final OpsRuntimeResourceAssembler resourceAssembler;
    private final OpsNodeRagService nodeRagService;
    private final OpsRuntimePromptAssembler promptAssembler;
    private final OpsRuntimeLlmInvoker llmInvoker;
    private final OpsGraphNodePromptContextPolicy promptContextPolicy;

    OpsGraphGenericLlmNodeExecutor(OpsRuntimeResourceAssembler resourceAssembler,
                                   OpsNodeRagService nodeRagService,
                                   OpsRuntimePromptAssembler promptAssembler,
                                   OpsRuntimeLlmInvoker llmInvoker,
                                   OpsGraphNodePromptContextPolicy promptContextPolicy) {
        this.resourceAssembler = resourceAssembler;
        this.nodeRagService = nodeRagService;
        this.promptAssembler = promptAssembler;
        this.llmInvoker = llmInvoker;
        this.promptContextPolicy = promptContextPolicy;
    }

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        OpsRuntimeResourceBundle bundle = resourceAssembler.assembleNode(
                context.definition(),
                context.node(),
                context.request(),
                context.events(),
                context.eventSink());
        // LLM mode is one pure model call. RAG/Skill may enrich context, but tool execution belongs to DIRECT/REACT.
        bundle.setTools(java.util.List.of());
        String prompt = promptAssembler.buildNodePrompt(
                context.definition(),
                context.node(),
                context.request().getQuery(),
                context.input(),
                context.state(),
                promptContextPolicy.promptPolicy(context.hooks()));
        prompt = nodeRagService.enhancePrompt(
                prompt,
                context.request().getQuery(),
                bundle.getRagEnabled(),
                bundle.getKnowledgeBaseId(),
                context.events(),
                context.eventSink(),
                false,
                bundle.getProjectId(),
                bundleKnowledgeBaseScope(bundle));
        String output = llmInvoker.call(
                promptAssembler.systemPrompt(context.definition(), context.node(), bundle),
                prompt,
                bundle,
                context.events(),
                context.eventSink(),
                context.hooks().requestStartedNanos(context.request()));
        return new OpsGraphNodeExecutionResult(output, null);
    }

    private String bundleKnowledgeBaseScope(OpsRuntimeResourceBundle bundle) {
        if (bundle == null || bundle.getMetadata() == null) {
            return "";
        }
        return String.valueOf(bundle.getMetadata().getOrDefault("knowledgeBaseScope", ""));
    }
}
