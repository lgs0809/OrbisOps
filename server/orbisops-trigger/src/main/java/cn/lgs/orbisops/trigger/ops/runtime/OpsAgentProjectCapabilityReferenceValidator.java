package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityReferenceValidationPort;
import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.config.McpClientDefinition;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentReferenceSyntaxPolicy;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Supplier;

/** Validates project membership and project-scoped Skill/MCP references. */
public final class OpsAgentProjectCapabilityReferenceValidator
        implements AgentCapabilityReferenceValidationPort {

    private final AgentReferenceSyntaxPolicy referenceSyntaxPolicy =
            new AgentReferenceSyntaxPolicy();
    private final Supplier<McpClientCatalogPort> mcpRepositorySupplier;
    private final Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier;
    private final Supplier<ProjectDefinitionApplicationService> projectDefinitionSupplier;
    private final Supplier<ProjectMcpAuthorizationApplicationService> mcpAuthorizationSupplier;
    private final Supplier<ProjectSkillAuthorizationApplicationService> skillAuthorizationSupplier;
    private final Supplier<ProjectMcpRuntimeDescriptorApplicationService> mcpDescriptorSupplier;
    private final Supplier<OpsSourceRepositoryService> sourceRepositorySupplier;

    public OpsAgentProjectCapabilityReferenceValidator(
            Supplier<McpClientCatalogPort> mcpRepositorySupplier,
            Supplier<SkillRuntimeToolProvider> skillToolProviderSupplier,
            Supplier<ProjectDefinitionApplicationService> projectDefinitionSupplier,
            Supplier<ProjectMcpAuthorizationApplicationService> mcpAuthorizationSupplier,
            Supplier<ProjectSkillAuthorizationApplicationService> skillAuthorizationSupplier,
            Supplier<ProjectMcpRuntimeDescriptorApplicationService> mcpDescriptorSupplier,
            Supplier<OpsSourceRepositoryService> sourceRepositorySupplier) {
        this.mcpRepositorySupplier = required(
                mcpRepositorySupplier, "MCP_REPOSITORY_SUPPLIER_REQUIRED");
        this.skillToolProviderSupplier = required(
                skillToolProviderSupplier, "SKILL_TOOL_PROVIDER_SUPPLIER_REQUIRED");
        this.projectDefinitionSupplier = required(
                projectDefinitionSupplier, "PROJECT_DEFINITION_SUPPLIER_REQUIRED");
        this.mcpAuthorizationSupplier = required(
                mcpAuthorizationSupplier, "MCP_AUTHORIZATION_SUPPLIER_REQUIRED");
        this.skillAuthorizationSupplier = required(
                skillAuthorizationSupplier, "SKILL_AUTHORIZATION_SUPPLIER_REQUIRED");
        this.mcpDescriptorSupplier = required(
                mcpDescriptorSupplier, "MCP_DESCRIPTOR_SUPPLIER_REQUIRED");
        this.sourceRepositorySupplier = required(
                sourceRepositorySupplier, "SOURCE_REPOSITORY_SUPPLIER_REQUIRED");
    }

    public void validateProject(String projectId) {
        ProjectDefinitionApplicationService projectDefinition =
                projectDefinitionSupplier.get();
        if (StringUtils.hasText(projectId)
                && projectDefinition != null
                && !projectDefinition.exists(projectId)) {
            throw new IllegalArgumentException(
                    "Agent 所属项目不存在：" + projectId);
        }
    }

    public void validateSkills(
            List<String> skills,
            String owner,
            String projectId) {
        SkillRuntimeToolProvider skillToolProvider = skillToolProviderSupplier.get();
        ProjectSkillAuthorizationApplicationService authorization =
                skillAuthorizationSupplier.get();
        for (String skill : skills == null ? List.<String>of() : skills) {
            referenceSyntaxPolicy.validateSkill(skill, owner);
            if (skillToolProvider != null
                    && skillToolProvider.listSkillSummaries().stream()
                    .noneMatch(item -> skill.equals(item.name()))) {
                throw new IllegalArgumentException(
                        owner + " 引用了不存在的 skill：" + skill);
            }
            if (StringUtils.hasText(projectId)
                    && authorization != null
                    && !authorization.allows(projectId, skill)) {
                throw new IllegalArgumentException(
                        owner + " 引用了未授权给项目 " + projectId
                                + " 的 skill：" + skill);
            }
        }
    }

    public void validateMcpReferences(
            List<String> ids,
            String owner,
            String projectId) {
        for (String id : ids == null ? List.<String>of() : ids) {
            validateReference(id, owner);
            if (StringUtils.hasText(id)) {
                validateMcpReference(id, owner, projectId);
            }
        }
    }

    private void validateMcpReference(
            String id,
            String owner,
            String projectId) {
        McpClientCatalogPort mcpRepository = mcpRepositorySupplier.get();
        ProjectMcpAuthorizationApplicationService authorization =
                mcpAuthorizationSupplier.get();
        ProjectMcpRuntimeDescriptorApplicationService descriptor =
                mcpDescriptorSupplier.get();
        OpsSourceRepositoryService sourceRepository = sourceRepositorySupplier.get();

        McpClientDefinition mcp = mcpRepository == null
                ? null
                : mcpRepository.findByMcpId(id);
        boolean genericMcpEnabled = mcp != null && enabled(mcp.status());
        boolean projectMcpEnabled = authorization != null
                && authorization.allows(projectId, id);
        if (genericMcpEnabled
                && StringUtils.hasText(projectId)
                && authorization != null
                && !projectMcpEnabled) {
            throw new IllegalArgumentException(
                    owner + " 引用了未绑定给项目 " + projectId
                            + " 的通用 MCP：" + id);
        }
        boolean belongsToAnotherProject = StringUtils.hasText(projectId)
                && descriptor != null
                && descriptor.existsEnabledAny(id)
                && !projectMcpEnabled;
        if (belongsToAnotherProject) {
            throw new IllegalArgumentException(
                    owner + " 引用了其他项目的 MCP：" + id);
        }
        boolean sourceMcpEnabled = sourceRepository != null
                && sourceRepository.resolveMcpServer(projectId, id).isPresent();
        if (!genericMcpEnabled && !projectMcpEnabled && !sourceMcpEnabled) {
            throw new IllegalArgumentException(
                    owner + " 引用了不存在或未启用的 MCP：" + id);
        }
    }

    private void validateReference(String id, String owner) {
        referenceSyntaxPolicy.validateResourceId(id, owner);
    }

    private boolean enabled(Integer status) {
        return status != null && status == 1;
    }

    private <T> Supplier<T> required(Supplier<T> supplier, String code) {
        if (supplier == null) {
            throw new IllegalArgumentException(code);
        }
        return supplier;
    }
}
