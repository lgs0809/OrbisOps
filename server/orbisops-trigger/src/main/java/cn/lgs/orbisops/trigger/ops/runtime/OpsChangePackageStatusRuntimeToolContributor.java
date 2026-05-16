package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackageToolProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Opt-in read-only discovery for workflow input resolution; exposes no proposal or approval tools. */
@Component
public final class OpsChangePackageStatusRuntimeToolContributor implements OpsRuntimeToolContributor {
    private final ObjectProvider<OpsChangePackageToolProvider> providers;
    public OpsChangePackageStatusRuntimeToolContributor(ObjectProvider<OpsChangePackageToolProvider> providers) {
        this.providers = java.util.Objects.requireNonNull(providers);
    }
    @Override public String id() { return "change-package-status"; }
    @Override public int order() { return 201; }
    @Override public OpsRuntimeToolContributorRequirement requirement() { return OpsRuntimeToolContributorRequirement.REQUIRED; }
    @Override public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        var node = context.getNode();
        boolean enabled = node != null && node.getConfig() != null
                && Boolean.TRUE.equals(node.getConfig().get("changePackageStatusEnabled"));
        if (context.getAgentScope() != null) {
            enabled = Boolean.TRUE.equals(context.getAgentScope().getChangePackageStatusEnabled());
        }
        if (!enabled) return;
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        if (context.getProjectId() == null || context.getProjectId().isBlank() || runId.isBlank() || actor.isBlank()) {
            throw new SecurityException("CHANGE_PACKAGE_QUERY_IDENTITY_REQUIRED");
        }
        if (context.getTools().stream().anyMatch(tool -> tool.getToolDefinition() != null
                && "QueryChangePackageStatus".equals(tool.getToolDefinition().name()))) return;
        var provider = providers.getIfAvailable();
        if (provider == null) throw new IllegalStateException("CHANGE_PACKAGE_QUERY_PROVIDER_REQUIRED");
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                provider.buildStatusQuery(context.getProjectId(), actor, runId, context.getRequest()),
                OpsRuntimeToolAuthorityDescriptor.readOnly("CHANGE_PACKAGE_STATUS",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE),
                        Set.of("QueryChangePackageStatus"))));
    }
}
