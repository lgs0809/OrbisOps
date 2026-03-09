package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.function.Supplier;

@Component
public final class OpsChannelRuntimeToolContributor implements OpsRuntimeToolContributor {

    private final Supplier<OpsChannelToolProvider> providerSupplier;

    @Autowired
    public OpsChannelRuntimeToolContributor(ObjectProvider<OpsChannelToolProvider> provider) {
        this(provider::getIfAvailable);
    }

    OpsChannelRuntimeToolContributor(Supplier<OpsChannelToolProvider> providerSupplier) {
        if (providerSupplier == null) throw new IllegalArgumentException("CHANNEL_TOOL_SUPPLIER_REQUIRED");
        this.providerSupplier = providerSupplier;
    }

    @Override
    public String id() { return "channel"; }

    @Override
    public int order() { return 400; }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        OpsChannelToolProvider provider = providerSupplier.get();
        if (provider == null || !StringUtils.hasText(context.getProjectId())) return;
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        if (!StringUtils.hasText(actor) || !StringUtils.hasText(runId)
                || !provider.available(context.getProjectId())) {
            return;
        }
        ToolCallback callback = provider.build(context.getProjectId(), actor, runId);
        String name = toolName(callback);
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                callback,
                OpsRuntimeToolAuthorityDescriptor.delegated(
                        "CHANNEL",
                        Set.of(
                                AgentExecutionStage.INVESTIGATE,
                                AgentExecutionStage.PREPARE),
                        Set.of(name))));
        context.getMetadata().put("channelToolEnabled", true);
    }

    private String toolName(ToolCallback callback) {
        return callback == null || callback.getToolDefinition() == null
                || callback.getToolDefinition().name() == null
                ? ""
                : callback.getToolDefinition().name().trim();
    }
}
