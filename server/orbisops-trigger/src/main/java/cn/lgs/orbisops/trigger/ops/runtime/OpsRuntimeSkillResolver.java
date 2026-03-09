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
        SkillRuntimeToolProvider skillToolProvider = skillToolProviderSupplier.get();
        Set<String> skills = context.getSkillNames();
        String frozenContext = frozenContextResolver.render(context);
        if ((skills == null || skills.isEmpty()) && frozenContext.isBlank()) {
            context.getMetadata().put("skillContextMode", "none");
            return;
        }
        if (skillToolProvider != null && skills != null && !skills.isEmpty()) {
            skillToolProvider.buildSkillToolCallback(skills)
                    .map(callback -> new OpsRuntimeSkillActivationToolCallback(
                            callback,
                            skills,
                            context::record))
                    .map(callback -> OpsRuntimeGovernedToolCallback.wrap(
                            callback,
                            OpsRuntimeToolAuthorityDescriptor.readOnly(
                                    "SKILL_RUNTIME",
                                    Set.of(
                                            AgentExecutionStage.INVESTIGATE,
                                            AgentExecutionStage.PREPARE),
                                    aliases(callback))))
                    .ifPresent(context.getTools()::add);
        }
        List<Map<String, Object>> catalogRefs = frozenContextResolver.catalogRefs(context);
        OpsProjectSkillToolProvider projectSkillToolProvider = projectSkillToolProviderSupplier.get();
        if (projectSkillToolProvider != null && !catalogRefs.isEmpty()) {
            org.springframework.ai.tool.ToolCallback callback = projectSkillToolProvider.build(
                    context.getProjectId(),
                    context.getRequest().getUserId(),
                    context.getRequest().getRunId(),
                    catalogRefs);
            context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                    callback,
                    OpsRuntimeToolAuthorityDescriptor.readOnly(
                            "PROJECT_SKILL_CATALOG",
                            Set.of(
                                    AgentExecutionStage.INVESTIGATE,
                                    AgentExecutionStage.PREPARE),
                            aliases(callback))));
        }
        if (!settings.contextEnabled()) {
            context.getMetadata().put("skillContextMode", "disabled");
            return;
        }
        String mode = normalizeMode(settings.contextMode());
        String rendered = skillToolProvider == null || skills == null || skills.isEmpty()
                ? ""
                : renderBoundSkills(skillToolProvider, skills, mode);
        if (StringUtils.hasText(frozenContext)) {
            rendered = StringUtils.hasText(rendered)
                    ? rendered + "\n\n" + frozenContext
                    : frozenContext;
        }
        context.getMetadata().put("skillContextMode", mode);
        context.setSkillContext(rendered);
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

    private String renderBoundSkills(
            SkillRuntimeToolProvider provider,
            Set<String> skills,
            String mode) {
        return switch (mode) {
            case "full" -> provider.renderSkillContext(skills, settings.contextMaxChars());
            case "summary" -> provider.renderSkillSummaryContext(
                    skills, settings.contextMaxChars());
            default -> lazyContext(provider, skills);
        };
    }

    private String lazyContext(SkillRuntimeToolProvider provider, Set<String> skills) {
        String summary = provider.renderSkillSummaryContext(
                skills, settings.contextMaxChars());
        if (!StringUtils.hasText(summary)) {
            summary = "- " + String.join("\n- ", skills);
        }
        return """
                当前 Agent / 节点绑定了以下 Skill。Skill 是操作规程，不是事实证据；事实只能来自用户输入、RAG、MCP 查询结果和 observation。
                如当前任务需要具体操作规程，必须先调用 Skill 工具读取对应 Skill 正文；不要使用未绑定 Skill，不要把 Skill 当成查询结果。

                %s
                """.formatted(summary).trim();
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
