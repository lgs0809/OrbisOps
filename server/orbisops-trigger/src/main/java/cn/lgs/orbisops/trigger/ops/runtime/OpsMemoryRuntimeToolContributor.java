package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/** Adds explicit Memory read/write tools to normal Daily Chat only. */
@Component
public final class OpsMemoryRuntimeToolContributor implements OpsRuntimeToolContributor {

    private final ObjectProvider<OpsMemoryToolProvider> provider;

    public OpsMemoryRuntimeToolContributor(ObjectProvider<OpsMemoryToolProvider> provider) {
        if (provider == null) throw new IllegalArgumentException("MEMORY_TOOL_PROVIDER_REQUIRED");
        this.provider = provider;
    }

    @Override
    public String id() {
        return "memory";
    }

    @Override
    public int order() {
        return 190;
    }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        var execution = context.getExecutionContext();
        if (execution == null || execution.stage() != AgentExecutionStage.PREPARE) return;
        String projectId = context.getProjectId();
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        String sessionId = context.getRequest() == null ? "" : context.getRequest().getSessionId();
        if (!StringUtils.hasText(projectId)
                || !StringUtils.hasText(actor)
                || !StringUtils.hasText(runId)) {
            OpsRuntimeToolContributionSupport.warn(context, "Memory Tool 缺少 project/actor/runId，未暴露。");
            return;
        }
        OpsMemoryToolProvider toolProvider = provider.getIfAvailable();
        if (toolProvider == null) throw new IllegalStateException("MEMORY_TOOL_PROVIDER_UNAVAILABLE");
        for (ToolCallback callback : toolProvider.build(projectId, actor, sessionId, runId)) {
            String name = callback.getToolDefinition() == null ? "" : callback.getToolDefinition().name();
            OpsRuntimeToolAuthorityDescriptor descriptor = OpsMemoryToolProvider.SEARCH_TOOL.equals(name)
                    ? OpsRuntimeToolAuthorityDescriptor.readOnly(
                            "EXPLICIT_MEMORY_READ",
                            Set.of(AgentExecutionStage.PREPARE),
                            Set.of(name))
                    : OpsRuntimeToolAuthorityDescriptor.workflow(
                            "EXPLICIT_MEMORY_WRITE",
                            Set.of(AgentExecutionStage.PREPARE),
                            Set.of(name));
            context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(callback, descriptor));
        }
        context.getMetadata().put("explicitMemoryToolsEnabled", true);
    }
}
