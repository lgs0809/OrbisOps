package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.skill.SkillProjectValidationPort;
import org.springframework.stereotype.Component;

@Component
public class OpsSkillProjectValidationAdapter implements SkillProjectValidationPort {

    private final ProjectDefinitionApplicationService projectDefinitionService;

    public OpsSkillProjectValidationAdapter(
            ProjectDefinitionApplicationService projectDefinitionService) {
        this.projectDefinitionService = projectDefinitionService;
    }

    @Override
    public void requireExisting(String projectId) {
        String normalized = projectId == null ? "" : projectId.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        if (!projectDefinitionService.exists(normalized)) {
            throw new IllegalArgumentException("项目不存在：" + normalized);
        }
    }

    @Override
    public java.util.List<String> configuredSkillIds(String projectId) {
        requireExisting(projectId);
        return projectDefinitionService.findDefinition(projectId).orElseThrow().skillIds();
    }
}
