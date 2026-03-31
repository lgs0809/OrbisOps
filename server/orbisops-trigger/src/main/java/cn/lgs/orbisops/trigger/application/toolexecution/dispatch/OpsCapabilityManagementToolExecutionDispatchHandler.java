package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.domain.project.model.ProjectAction;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import cn.lgs.orbisops.trigger.ops.capability.OpsCapabilityImportService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Atomic capability mutations. Higher-level creation/import methodology belongs to System Skills. */
@Component
public final class OpsCapabilityManagementToolExecutionDispatchHandler
        implements OpsToolExecutionDispatchHandler {

    private final ObjectProvider<AuthorizeProjectAccessUseCase> access;
    private final ObjectProvider<SkillManagementUseCase> skills;
    private final ObjectProvider<OpsCapabilityImportService> imports;

    public OpsCapabilityManagementToolExecutionDispatchHandler(
            ObjectProvider<AuthorizeProjectAccessUseCase> access,
            ObjectProvider<SkillManagementUseCase> skills,
            ObjectProvider<OpsCapabilityImportService> imports) {
        this.access = access;
        this.skills = skills;
        this.imports = imports;
    }

    @Override
    public String handlerId() {
        return "capability-management";
    }

    @Override
    public int order() {
        return 185;
    }

    @Override
    public boolean supports(ToolExecutionTarget target) {
        return "capability.manage".equals(target.toolsetId())
                || "CAPABILITY_MANAGEMENT".equalsIgnoreCase(target.adapterType());
    }

    @Override
    public Object dispatch(ToolExecutionTarget target, ToolExecutionRequest request) {
        Identity identity = authorize(request);
        return switch (target.toolName()) {
            case "skill_project_create" -> createProjectSkill(request, identity);
            case "skill_package_import" -> importSkill(request, identity);
            case "mcp_server_import" -> importMcp(request, identity);
            default -> throw new IllegalArgumentException(
                    "未知 Capability Management 工具：" + target.toolName());
        };
    }

    private Map<String, Object> createProjectSkill(
            ToolExecutionRequest request,
            Identity identity) {
        SkillManagementUseCase useCase = available(skills, "Skill management use case 未初始化");
        Map<String, Object> payload = new LinkedHashMap<>(request.arguments());
        payload.remove("projectId");
        payload.remove("actor");
        payload.put("status", "DRAFT");
        payload.put("updateMode", "MANUAL_ONLY");
        return useCase.createProjectSkill(
                request.projectId(),
                Map.copyOf(payload),
                identity.actor());
    }

    private Map<String, Object> importSkill(
            ToolExecutionRequest request,
            Identity identity) {
        OpsCapabilityImportService service = available(imports, "Capability import service 未初始化");
        String sourceUrl = sourceUrl(request.arguments());
        return service.importSkill(
                request.projectId(),
                sourceUrl,
                importOptions(request.arguments()),
                identity.username(),
                identity.userId(),
                false);
    }

    private Map<String, Object> importMcp(
            ToolExecutionRequest request,
            Identity identity) {
        OpsCapabilityImportService service = available(imports, "Capability import service 未初始化");
        String sourceUrl = sourceUrl(request.arguments());
        return service.importMcp(
                request.projectId(),
                sourceUrl,
                importOptions(request.arguments()),
                identity.username(),
                identity.userId(),
                false);
    }

    private Identity authorize(ToolExecutionRequest request) {
        AuthorizeProjectAccessUseCase useCase = available(access, "Project access use case 未初始化");
        String userId = firstText(request.userId(), request.actor());
        String username = firstText(
                request.requestContext().get("authenticatedUsername"),
                request.requestContext().get("username"));
        useCase.requireAction(
                request.projectId(),
                username,
                userId,
                false,
                ProjectAction.MANAGE_CAPABILITY);
        return new Identity(username, userId, firstText(userId, username));
    }

    private Map<String, Object> importOptions(Map<String, Object> arguments) {
        Map<String, Object> copy = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        copy.remove("sourceUrl");
        copy.remove("url");
        copy.remove("projectId");
        copy.remove("actor");
        return Map.copyOf(copy);
    }

    private String sourceUrl(Map<String, Object> arguments) {
        return required(firstText(
                arguments == null ? null : arguments.get("sourceUrl"),
                arguments == null ? null : arguments.get("url")),
                "CAPABILITY_SOURCE_URL_REQUIRED");
    }

    private <T> T available(ObjectProvider<T> provider, String message) {
        T value = provider == null ? null : provider.getIfAvailable();
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private String required(String value, String reasonCode) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(reasonCode);
        return value.trim();
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Identity(String username, String userId, String actor) {
    }
}
