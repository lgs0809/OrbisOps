package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Resolves bound Skill tools and coordinates runtime Skill context materialization. */
public final class OpsRuntimeSkillResolver {

    private final Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier;
    private final Supplier<ProjectSkillAuthorizationApplicationService> authorizationSupplier;
    private final Supplier<OpsProjectSkillToolProvider> projectSkillToolProviderSupplier;
    private final OpsRuntimeFrozenSkillContextResolver frozenContextResolver;
    private final OpsRuntimeSkillSettings settings;

    public OpsRuntimeSkillResolver(
            Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier,
            Supplier<ProjectSkillAuthorizationApplicationService> authorizationSupplier,
            Supplier<OpsProjectSkillToolProvider> projectSkillToolProviderSupplier,
            OpsRuntimeFrozenSkillContextResolver frozenContextResolver,
            OpsRuntimeSkillSettings settings) {
        if (skillToolProviderSupplier == null) {
            throw new IllegalArgumentException("SKILL_TOOL_PROVIDER_SUPPLIER_REQUIRED");
        }
        if (authorizationSupplier == null) {
            throw new IllegalArgumentException("PROJECT_SKILL_AUTHORIZATION_SUPPLIER_REQUIRED");
        }
        if (projectSkillToolProviderSupplier == null) {
            throw new IllegalArgumentException("PROJECT_SKILL_TOOL_PROVIDER_SUPPLIER_REQUIRED");
        }
        if (frozenContextResolver == null) {
            throw new IllegalArgumentException("FROZEN_SKILL_CONTEXT_RESOLVER_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("RUNTIME_SKILL_SETTINGS_REQUIRED");
        }
        this.skillToolProviderSupplier = skillToolProviderSupplier;
        this.authorizationSupplier = authorizationSupplier;
        this.projectSkillToolProviderSupplier = projectSkillToolProviderSupplier;
        this.frozenContextResolver = frozenContextResolver;
        this.settings = settings;
    }

    public void resolve(OpsRuntimeResourceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        }
        String frozenContext = frozenContextResolver.render(context);
        List<Map<String, Object>> catalogRefs = frozenContextResolver.catalogRefs(context);
        catalogTool(context).ifPresent(context.getTools()::add);
        if (!settings.contextEnabled()) {
            context.getMetadata().put("skillContextMode", "disabled");
            return;
        }
        String mode = normalizeMode(settings.contextMode());
        String rendered = frozenContext;
        if (rendered.isBlank() && catalogRefs.isEmpty()) {
            context.getMetadata().put("skillContextMode", "none");
            return;
        }
        context.getMetadata().put("skillContextMode", mode);
        context.setSkillContext(rendered);
    }

    public java.util.Optional<org.springframework.ai.tool.ToolCallback> catalogTool(OpsRuntimeResourceContext context) {
        List<Map<String, Object>> refs = frozenContextResolver.catalogRefs(context);
        OpsProjectSkillToolProvider provider = projectSkillToolProviderSupplier.get();
        if (refs.isEmpty()) return java.util.Optional.empty();
        if (provider == null) throw new IllegalStateException("PROJECT_SKILL_TOOL_PROVIDER_REQUIRED");
        org.springframework.ai.tool.ToolCallback callback = provider.build(context.getProjectId(),
                context.getRequest().getUserId(), context.getRequest().getRunId(), refs);
        return java.util.Optional.of(OpsRuntimeGovernedToolCallback.wrap(callback,
                OpsRuntimeToolAuthorityDescriptor.readOnly("PROJECT_SKILL_CATALOG",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE), aliases(callback))));
    }

    public List<String> enabledProjectSkillIds(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return List.of();
        }
        ProjectSkillAuthorizationApplicationService authorization = authorizationSupplier.get();
        if (authorization == null) {
            return List.of();
        }
        List<String> enabledIds = authorization.enabledIds(projectId);
        return enabledIds == null ? List.of() : List.copyOf(enabledIds);
    }

    private String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return "lazy";
        }
        String mode = value.trim().toLowerCase();
        if ("full".equals(mode) || "summary".equals(mode) || "lazy".equals(mode)) {
            return mode;
        }
        return "lazy";
    }

    private Set<String> aliases(org.springframework.ai.tool.ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null
                || !StringUtils.hasText(callback.getToolDefinition().name())) {
            return Set.of();
        }
        return Set.of(callback.getToolDefinition().name().trim());
    }
}
