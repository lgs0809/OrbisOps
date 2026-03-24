package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityBindingValidationResult;
import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityBindingValidationUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentCapabilitySanitizationUseCase;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilityBindingPolicy;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentCapabilitySanitizationPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Trigger facade over Agent capability validation and sanitization use cases. */
@Service
public class OpsAgentCapabilityBindingPolicy {

    private final OpsAgentCapabilityBindingMapper bindingMapper =
            new OpsAgentCapabilityBindingMapper();
    private final OpsAgentCapabilitySanitizationMapper sanitizationMapper =
            new OpsAgentCapabilitySanitizationMapper();
    private final OpsAgentCapabilityAuthorizationAdapter authorizationAdapter;
    private final AgentCapabilityBindingValidationUseCase validationUseCase;
    private final AgentCapabilitySanitizationUseCase sanitizationUseCase;

    public OpsAgentCapabilityBindingPolicy(
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationService,
            ProjectMcpAuthorizationApplicationService projectMcpAuthorizationService,
            ProjectSkillAuthorizationApplicationService projectSkillAuthorizationService,
            ExecutionResourceQueryApplicationService executionResourceService) {
        this.authorizationAdapter = new OpsAgentCapabilityAuthorizationAdapter(
                projectDefinitionService,
                projectKnowledgeAuthorizationService,
                projectMcpAuthorizationService,
                projectSkillAuthorizationService,
                executionResourceService);
        this.validationUseCase = new AgentCapabilityBindingValidationUseCase(
                authorizationAdapter,
                new AgentCapabilityBindingPolicy());
        this.sanitizationUseCase = new AgentCapabilitySanitizationUseCase(
                new OpsAgentCapabilityCatalogAdapter(
                        projectKnowledgeAuthorizationService,
                        projectMcpAuthorizationService,
                        projectSkillAuthorizationService,
                        executionResourceService),
                new AgentCapabilitySanitizationPolicy());
    }

    public Map<String, Object> validate(OpsAgentDefinition definition) {
        return view(validateBindings(definition));
    }

    public Map<String, Object> validate(
            String agentId,
            OpsAgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Agent 绑定校验请求不能为空");
        }
        if (StringUtils.hasText(definition.getAgentId())
                && !agentId.equals(definition.getAgentId().trim())) {
            throw new IllegalArgumentException("路径 agentId 与请求体 agentId 不一致");
        }
        definition.setAgentId(agentId);
        return view(validateBindings(definition));
    }

    public void assertValid(OpsAgentDefinition definition) {
        validateBindings(definition).requireValid();
    }

    public String requireExistingProject(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalArgumentException("必须提供 projectId");
        }
        String normalizedProjectId = projectId.trim();
        if (!authorizationAdapter.projectExists(normalizedProjectId)) {
            throw new IllegalArgumentException("项目不存在：" + normalizedProjectId);
        }
        return normalizedProjectId;
    }

    public void sanitizeForProject(
            OpsAgentDefinition definition,
            String projectId) {
        sanitizationMapper.apply(
                definition,
                sanitizationUseCase.sanitize(
                        sanitizationMapper.request(definition, projectId)));
    }

    private AgentCapabilityBindingValidationResult validateBindings(
            OpsAgentDefinition definition) {
        return validationUseCase.validate(bindingMapper.mapForValidation(definition));
    }

    private Map<String, Object> view(
            AgentCapabilityBindingValidationResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", result.valid());
        data.put("projectId", result.projectId());
        data.put("errors", result.errors());
        data.put("warnings", result.warnings());
        data.put("skillRefs", new ArrayList<>(result.references().skillRefs()));
        data.put("mcpRefs", new ArrayList<>(result.references().projectToolRefs()));
        data.put("knowledgeBaseRefs", new ArrayList<>(
                result.references().knowledgeBaseRefs()));
        data.put("executionTargetRefs", new ArrayList<>(
                result.references().executionTargetRefs()));
        return data;
    }
}
