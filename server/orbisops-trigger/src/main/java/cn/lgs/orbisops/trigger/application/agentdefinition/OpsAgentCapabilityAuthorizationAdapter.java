package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityAuthorizationPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;

/** Outbound adapter for project-scoped Agent capability authorization. */
final class OpsAgentCapabilityAuthorizationAdapter
        implements AgentCapabilityAuthorizationPort {

    private final ProjectDefinitionApplicationService projectDefinitionService;
    private final ProjectKnowledgeAuthorizationApplicationService knowledgeService;
    private final ProjectMcpAuthorizationApplicationService mcpService;
    private final ProjectSkillAuthorizationApplicationService skillService;
    private final ExecutionResourceQueryApplicationService executionResourceService;

    OpsAgentCapabilityAuthorizationAdapter(
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectKnowledgeAuthorizationApplicationService knowledgeService,
            ProjectMcpAuthorizationApplicationService mcpService,
            ProjectSkillAuthorizationApplicationService skillService,
            ExecutionResourceQueryApplicationService executionResourceService) {
        this.projectDefinitionService = projectDefinitionService;
        this.knowledgeService = knowledgeService;
        this.mcpService = mcpService;
        this.skillService = skillService;
        this.executionResourceService = executionResourceService;
    }

    @Override
    public boolean projectExists(String projectId) {
        return projectDefinitionService != null
                && projectDefinitionService.exists(projectId);
    }

    @Override
    public boolean skillAllowed(String projectId, String skillId) {
        return skillService != null && skillService.allows(projectId, skillId);
    }

    @Override
    public boolean projectToolAllowed(String projectId, String toolId) {
        return mcpService != null && mcpService.allows(projectId, toolId);
    }

    @Override
    public boolean knowledgeBaseAllowed(String projectId, String knowledgeBaseId) {
        return knowledgeService != null
                && knowledgeService.allows(projectId, knowledgeBaseId);
    }

    @Override
    public boolean executionTargetEnabled(String projectId, String executionTargetId) {
        return executionResourceService != null
                && executionResourceService.find(projectId, executionTargetId)
                .filter(resource -> "ENABLED".equalsIgnoreCase(resource.status().name()))
                .isPresent();
    }
}
