package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/** Exposes governed read-only datasource tools to generic ReAct in investigate/prepare stages. */
@Component
public final class OpsDatasourceRuntimeToolContributor implements OpsRuntimeToolContributor {

    private final ObjectProvider<OpsDatasourceRuntimeToolProvider> provider;

    public OpsDatasourceRuntimeToolContributor(ObjectProvider<OpsDatasourceRuntimeToolProvider> provider) {
        if (provider == null) throw new IllegalArgumentException("DATASOURCE_RUNTIME_TOOL_PROVIDER_REQUIRED");
        this.provider = provider;
    }

    @Override
    public String id() {
        return "datasource";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        boolean changePackageGovernance = context.getRequest() != null
                && context.getRequest().getMetadata() != null
                && Boolean.TRUE.equals(context.getRequest().getMetadata().get(
                OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY));
        if (context.getAgentScope() == null && !changePackageGovernance) return;
        var execution = context.getExecutionContext();
        if (execution != null && execution.stage() != AgentExecutionStage.INVESTIGATE
                && execution.stage() != AgentExecutionStage.PREPARE) {
            return;
        }
        String projectId = context.getProjectId();
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        OpsAgentRunRequestDTO analysisRequest = analysisRequest(context);
        if (!StringUtils.hasText(projectId)
                || !StringUtils.hasText(runId)
                || analysisRequest == null) {
            OpsRuntimeToolContributionSupport.warn(
                    context,
                    "Datasource Tool 缺少 project/runId/analysisRequest，未暴露。");
            return;
        }
        OpsDatasourceRuntimeToolProvider toolProvider = provider.getIfAvailable();
        if (toolProvider == null) throw new IllegalStateException("DATASOURCE_RUNTIME_TOOL_PROVIDER_UNAVAILABLE");
        List<ToolCallback> callbacks = toolProvider.build(projectId, actor, runId, analysisRequest, context);
        for (ToolCallback callback : callbacks) {
            String name = callback.getToolDefinition() == null ? "" : callback.getToolDefinition().name();
            context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                    callback,
                    OpsRuntimeToolAuthorityDescriptor.readOnly(
                            "LOCAL_DATASOURCE",
                            Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE),
                            Set.of(name))));
        }
        context.getMetadata().put("datasourceToolsEnabled", !callbacks.isEmpty());
        context.getMetadata().put("datasourceToolCount", callbacks.size());
        context.record(OpsRuntimeEvent.builder()
                .eventType("DATASOURCE_TOOLS_EXPOSED")
                .status("SUCCEEDED")
                .summary("Generic ReAct 只读数据源工具装配完成。")
                .payload(java.util.Map.of(
                        "owner", context.ownerLabel(),
                        "tools", callbacks.stream()
                                .map(callback -> callback.getToolDefinition() == null
                                        ? ""
                                        : callback.getToolDefinition().name())
                                .toList()))
                .build());
    }

    private OpsAgentRunRequestDTO analysisRequest(OpsRuntimeResourceContext context) {
        return context == null ? null : OpsRuntimeToolContributionSupport.analysisRequest(context.getRequest());
    }
}
