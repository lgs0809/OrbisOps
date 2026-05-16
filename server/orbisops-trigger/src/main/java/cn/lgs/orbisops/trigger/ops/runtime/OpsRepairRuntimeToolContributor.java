package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairToolProvider;
import cn.lgs.orbisops.trigger.ops.source.OpsProjectServiceCatalogService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Component
public final class OpsRepairRuntimeToolContributor implements OpsRuntimeToolContributor {

    private static final Set<String> READ_ONLY_TOOLS = Set.of(
            "code_read", "code_grep", "code_glob", "code_lsp",
            "tool_result_read", "tool_result_grep", "tool_result_slice");
    private static final Set<String> REPAIR_WORKSPACE_TOOLS = Set.of(
            "ValidateCodeCandidate", "code_edit", "code_write", "code_bash",
            "code_enter_worktree", "code_exit_worktree", "code_compute_diff",
            "code_commit_repair");

    private final Supplier<OpsRepairToolProvider> repairToolProviderSupplier;
    private final Supplier<OpsProjectServiceCatalogService> projectServiceCatalogSupplier;

    @Autowired
    public OpsRepairRuntimeToolContributor(
            ObjectProvider<OpsRepairToolProvider> repairToolProvider,
            ObjectProvider<OpsProjectServiceCatalogService> projectServiceCatalog) {
        this(repairToolProvider::getIfAvailable, projectServiceCatalog::getIfAvailable);
    }

    OpsRepairRuntimeToolContributor(
            Supplier<OpsRepairToolProvider> repairToolProviderSupplier,
            Supplier<OpsProjectServiceCatalogService> projectServiceCatalogSupplier) {
        if (repairToolProviderSupplier == null || projectServiceCatalogSupplier == null) {
            throw new IllegalArgumentException("REPAIR_TOOL_SUPPLIERS_REQUIRED");
        }
        this.repairToolProviderSupplier = repairToolProviderSupplier;
        this.projectServiceCatalogSupplier = projectServiceCatalogSupplier;
    }

    @Override
    public String id() { return "repair"; }

    @Override
    public int order() { return 100; }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        OpsRepairToolProvider repairToolProvider = repairToolProviderSupplier.get();
        OpsProjectServiceCatalogService projectServiceCatalog = projectServiceCatalogSupplier.get();
        if (repairToolProvider == null || projectServiceCatalog == null
                || !Boolean.TRUE.equals(context.getRepairEnabled())
                || !Boolean.TRUE.equals(projectServiceCatalog.capabilities().get("enabled"))
                || !StringUtils.hasText(context.getProjectId())
                || projectServiceCatalog.list(context.getProjectId()).isEmpty()) {
            return;
        }
        String actor = context.getRequest() == null
                ? "ops-agent"
                : context.getRequest().getUserId();
        List<ToolCallback> callbacks = repairToolProvider.buildCodeTools(
                context.getProjectId(), actor, context.getRequest());
        for (ToolCallback callback : callbacks == null ? List.<ToolCallback>of() : callbacks) {
            context.getTools().add(governed(callback));
        }
        context.getMetadata().put("repairCandidateToolEnabled", true);
        context.getMetadata().put("controlledCodeToolsEnabled", true);
    }

    private ToolCallback governed(ToolCallback callback) {
        String name = toolName(callback);
        if (READ_ONLY_TOOLS.contains(name)) {
            return OpsRuntimeGovernedToolCallback.wrap(
                    callback,
                    OpsRuntimeToolAuthorityDescriptor.readOnly(
                            "REPAIR_TOOLSET",
                            Set.of(
                                    AgentExecutionStage.INVESTIGATE,
                                    AgentExecutionStage.PREPARE),
                            Set.of(name)));
        }
        if (REPAIR_WORKSPACE_TOOLS.contains(name)) {
            return OpsRuntimeGovernedToolCallback.wrap(
                    callback,
                    OpsRuntimeToolAuthorityDescriptor.repairWorkspace(
                            "REPAIR_TOOLSET",
                            Set.of(AgentExecutionStage.PREPARE),
                            Set.of(name)));
        }
        throw new IllegalStateException("RUNTIME_REPAIR_TOOL_SEMANTICS_UNDECLARED:" + name);
    }

    private String toolName(ToolCallback callback) {
        return callback == null || callback.getToolDefinition() == null
                || callback.getToolDefinition().name() == null
                ? ""
                : callback.getToolDefinition().name().trim();
    }
}
