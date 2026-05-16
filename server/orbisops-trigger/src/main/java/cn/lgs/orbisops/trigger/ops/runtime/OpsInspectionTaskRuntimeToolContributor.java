package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public final class OpsInspectionTaskRuntimeToolContributor implements OpsRuntimeToolContributor {

    private final Supplier<OpsInspectionTaskToolProvider> providerSupplier;
    private final Function<String, String> defaultAgentResolver;

    @Autowired
    public OpsInspectionTaskRuntimeToolContributor(
            ObjectProvider<OpsInspectionTaskToolProvider> provider,
            ObjectProvider<ProjectDefinitionApplicationService> projects) {
        this(
                provider::getIfAvailable,
                projectId -> {
                    ProjectDefinitionApplicationService service = projects == null ? null : projects.getIfAvailable();
                    return service == null ? "" : service.defaultAgentId(projectId);
                });
    }

    OpsInspectionTaskRuntimeToolContributor(Supplier<OpsInspectionTaskToolProvider> providerSupplier) {
        this(providerSupplier, projectId -> "");
    }

    OpsInspectionTaskRuntimeToolContributor(
            Supplier<OpsInspectionTaskToolProvider> providerSupplier,
            Function<String, String> defaultAgentResolver) {
        if (providerSupplier == null) throw new IllegalArgumentException("INSPECTION_TOOL_SUPPLIER_REQUIRED");
        if (defaultAgentResolver == null) throw new IllegalArgumentException("INSPECTION_DEFAULT_AGENT_RESOLVER_REQUIRED");
        this.providerSupplier = providerSupplier;
        this.defaultAgentResolver = defaultAgentResolver;
    }

    @Override
    public String id() { return "inspection-task"; }

    @Override
    public int order() { return 300; }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        OpsInspectionTaskToolProvider provider = providerSupplier.get();
        if (provider == null || !StringUtils.hasText(context.getProjectId())) return;
        String runId = OpsRuntimeToolContributionSupport.runtimeRunId(context.getRequest());
        String actor = OpsRuntimeToolContributionSupport.runtimeActor(context.getRequest());
        if (!StringUtils.hasText(actor)) {
            OpsRuntimeToolContributionSupport.warn(context, "巡检任务工具缺少真实操作者，未启用。");
            return;
        }
        String defaultAgentId = text(defaultAgentResolver.apply(context.getProjectId()));
        ToolCallback callback = provider.build(
                context.getProjectId(), actor, runId, defaultAgentId);
        String name = toolName(callback);
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                callback,
                OpsRuntimeToolAuthorityDescriptor.delegated(
                        "INSPECTION_TASK",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE),
                        Set.of(name))));
        context.getMetadata().put("inspectionTaskToolEnabled", true);
        context.getMetadata().put("inspectionTaskDefaultAgentId", defaultAgentId);
        context.getMetadata().put("inspectionDefaultExecutionStyle", "REACT");
        context.getMetadata().put("inspectionExplicitAgentExecutionStyle", "WORKFLOW");
    }

    private String toolName(ToolCallback callback) {
        return callback == null || callback.getToolDefinition() == null
                || callback.getToolDefinition().name() == null
                ? ""
                : callback.getToolDefinition().name().trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
