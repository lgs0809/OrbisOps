package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/** Exposes deterministic approval/rejection/Landing-start commands to the normal Daily Agent. */
@Component
public final class OpsChangePackageControlRuntimeToolContributor implements OpsRuntimeToolContributor {

    private final ObjectProvider<OpsChangePackageControlToolProvider> provider;

    public OpsChangePackageControlRuntimeToolContributor(
            ObjectProvider<OpsChangePackageControlToolProvider> provider) {
        if (provider == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CONTROL_TOOL_PROVIDER_REQUIRED");
        this.provider = provider;
    }

    @Override
    public String id() {
        return "change-package-control";
    }

    @Override
    public int order() {
        return 210;
    }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        if (!Boolean.TRUE.equals(context.getChangePackageEnabled())
                || !StringUtils.hasText(context.getProjectId())) {
            return;
        }
        var execution = context.getExecutionContext();
        if (execution == null || execution.stage() != AgentExecutionStage.PREPARE) return;
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(actor)) {
            OpsRuntimeToolContributionSupport.warn(
                    context, "ChangePackage 控制工具缺少 canonical runId 或真实操作者，未暴露。");
            return;
        }
        Object principalValue = context.getRequest() == null || context.getRequest().getMetadata() == null
                ? null
                : context.getRequest().getMetadata().get(OpsTrustedRequestMetadata.AUTH_PRINCIPAL);
        if (!(principalValue instanceof AdminAuthService.AuthPrincipal principal)) {
            OpsRuntimeToolContributionSupport.warn(
                    context, "ChangePackage 审批/驳回/Landing 控制工具缺少可信认证 principal，未暴露。");
            return;
        }
        OpsChangePackageControlToolProvider toolProvider = provider.getIfAvailable();
        if (toolProvider == null) {
            throw new IllegalStateException("CHANGE_PACKAGE_CONTROL_TOOL_PROVIDER_UNAVAILABLE");
        }
        for (ToolCallback callback : toolProvider.build(context.getProjectId(), actor, runId, principal)) {
            String toolName = callback.getToolDefinition() == null
                    ? ""
                    : callback.getToolDefinition().name();
            context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                    callback,
                    OpsRuntimeToolAuthorityDescriptor.changePackage(
                            "CHANGE_PACKAGE_CONTROL",
                            Set.of(AgentExecutionStage.PREPARE),
                            Set.of(toolName))));
        }
        context.getMetadata().put("changePackageControlToolsEnabled", true);
    }
}
