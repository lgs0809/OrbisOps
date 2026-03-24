package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityCatalogPort;
import cn.lgs.orbisops.application.execution.ExecutionResourceQueryApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentCapabilityCatalogSnapshot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Outbound adapter that resolves the authorized project capability catalog once. */
final class OpsAgentCapabilityCatalogAdapter implements AgentCapabilityCatalogPort {

    private final ProjectKnowledgeAuthorizationApplicationService knowledgeService;
    private final ProjectMcpAuthorizationApplicationService mcpService;
    private final ProjectSkillAuthorizationApplicationService skillService;
    private final ExecutionResourceQueryApplicationService executionResourceService;

    OpsAgentCapabilityCatalogAdapter(
            ProjectKnowledgeAuthorizationApplicationService knowledgeService,
            ProjectMcpAuthorizationApplicationService mcpService,
            ProjectSkillAuthorizationApplicationService skillService,
            ExecutionResourceQueryApplicationService executionResourceService) {
        this.knowledgeService = knowledgeService;
        this.mcpService = mcpService;
        this.skillService = skillService;
        this.executionResourceService = executionResourceService;
    }

    @Override
    public AgentCapabilityCatalogSnapshot resolve(
            String projectId,
            Set<String> requestedSkillIds,
            Set<String> requestedProjectToolIds,
            Set<String> requestedExecutionTargetIds) {
        List<String> knowledgeBaseIds = knowledgeService == null
                ? List.of()
                : knowledgeService.enabledIds(projectId);
        return new AgentCapabilityCatalogSnapshot(
                knowledgeBaseIds,
                allowedSkills(projectId, requestedSkillIds),
                allowedProjectTools(projectId, requestedProjectToolIds),
                allowedExecutionTargets(projectId, requestedExecutionTargetIds));
    }

    private Set<String> allowedSkills(String projectId, Set<String> requested) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String skillId : requested == null ? Set.<String>of() : requested) {
            if (skillService != null && skillService.allows(projectId, skillId)) {
                result.add(skillId);
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> allowedProjectTools(String projectId, Set<String> requested) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String toolId : requested == null ? Set.<String>of() : requested) {
            if (mcpService != null && mcpService.allows(projectId, toolId)) {
                result.add(toolId);
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> allowedExecutionTargets(String projectId, Set<String> requested) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String targetId : requested == null ? Set.<String>of() : requested) {
            if (executionResourceService != null
                    && executionResourceService.find(projectId, targetId).isPresent()) {
                result.add(targetId);
            }
        }
        return Set.copyOf(result);
    }
}
