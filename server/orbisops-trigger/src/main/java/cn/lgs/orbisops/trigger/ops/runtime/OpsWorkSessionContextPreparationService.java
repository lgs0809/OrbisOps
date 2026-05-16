package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemoryRuntimeInjectionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Thin facade for the authoritative Work Session context-preparation sequence. */
@Service
public class OpsWorkSessionContextPreparationService {

    static final String RUNTIME_MEMORY_CONTEXT_KEY =
            OpsWorkSessionContextMetadataKeys.RUNTIME_MEMORY_CONTEXT;
    static final String ORIGINAL_USER_QUERY_KEY =
            OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY;
    static final String REWRITTEN_QUERY_KEY =
            OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY;

    private final OpsWorkSessionMemoryContextAssembler memoryContextAssembler;
    private final OpsWorkSessionQueryRewriteCoordinator queryRewriteCoordinator;

    public OpsWorkSessionContextPreparationService(
            OpsConversationMemoryService memoryService,
            OpsMainQuestionRewriteService questionRewriteService,
            OpsRuntimeContextBundleAdapter runtimeContextBundleService,
            Optional<OpsMemoryRuntimeInjectionService> memoryRuntimeInjectionService) {
        OpsWorkSessionContextBundleCoordinator bundleCoordinator =
                new OpsWorkSessionContextBundleCoordinator(
                        runtimeContextBundleService);
        this.memoryContextAssembler = new OpsWorkSessionMemoryContextAssembler(
                memoryService,
                memoryRuntimeInjectionService == null
                        ? null
                        : memoryRuntimeInjectionService.orElse(null),
                bundleCoordinator);
        this.queryRewriteCoordinator =
                new OpsWorkSessionQueryRewriteCoordinator(questionRewriteService);
    }

    public void prepare(OpsAgentDefinition definition,
                        OpsAgentChatRequest request,
                        OpsRuntimeExecutionPlan plan,
                        List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> eventSink,
                        long requestStartedNanos) {
        if (request == null) return;
        request.setTrustedSkillBindings(OpsExplicitSkillBindings.capture(definition));
        String originalQuery = request.getQuery();
        request.getMetadata().putIfAbsent(
                ORIGINAL_USER_QUERY_KEY,
                value(originalQuery));
        boolean trustedLanding = trustedLanding(request);
        String memoryContext = memoryContextAssembler.assemble(
                request,
                plan.isMemoryEnabled() && !trustedLanding,
                events,
                eventSink,
                requestStartedNanos);
        request.getMetadata().put(
                RUNTIME_MEMORY_CONTEXT_KEY,
                memoryContext);
        queryRewriteCoordinator.rewrite(
                definition,
                request,
                plan,
                memoryContext,
                originalQuery,
                events,
                eventSink,
                requestStartedNanos);
    }

    private boolean trustedLanding(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return false;
        Object raw = request.getMetadata().get(OpsAgentRunExecutionContextFactory.AUTHORITATIVE_KEY);
        return raw instanceof AgentRunExecutionContext context
                && context.stage() == AgentExecutionStage.LANDING
                && context.approvedPackage().isPresent();
    }

    public String memoryContext(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return "";
        Object context = request.getMetadata().get(RUNTIME_MEMORY_CONTEXT_KEY);
        return context == null ? "" : String.valueOf(context);
    }

    public String originalUserQuery(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return "";
        Object original = request.getMetadata().get(ORIGINAL_USER_QUERY_KEY);
        return StringUtils.hasText(
                original == null ? null : String.valueOf(original))
                ? String.valueOf(original)
                : value(request.getQuery());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
