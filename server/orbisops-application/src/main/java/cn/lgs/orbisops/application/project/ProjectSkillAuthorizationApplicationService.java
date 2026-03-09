package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.application.skill.SkillAuthorizationCatalogPort;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectSkillAuthorizationApplicationService {

    private final ProjectDefinitionApplicationService definitionService;
    private final SkillAuthorizationCatalogPort skillCatalogService;

    public ProjectSkillAuthorizationApplicationService(
            ProjectDefinitionApplicationService definitionService,
            SkillAuthorizationCatalogPort skillCatalogService) {
        if (definitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        if (skillCatalogService == null) {
            throw new IllegalArgumentException("SKILL_CATALOG_QUERY_SERVICE_REQUIRED");
        }
        this.definitionService = definitionService;
        this.skillCatalogService = skillCatalogService;
    }

    public List<String> enabledIds(String projectId) {
        ProjectSkillAuthorization authorization = resolve(projectId);
        LinkedHashSet<String> ids = new LinkedHashSet<>(authorization.localIds());
        ids.addAll(authorization.globalIds());
        return List.copyOf(ids);
    }

    public List<String> localIds(String projectId) {
        return resolve(projectId).localIds();
    }

    public List<String> globalIds(String projectId) {
        return resolve(projectId).globalIds();
    }

    public boolean allows(String projectId, String skillId) {
        String project = text(projectId);
        String skill = text(skillId);
        return !project.isBlank()
                && !skill.isBlank()
                && enabledIds(project).contains(skill);
    }

    private ProjectSkillAuthorization resolve(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        ProjectDefinition project = definitionService.findDefinition(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
        List<String> configured = project.skillIds();
        Set<String> projectCatalogIds = new LinkedHashSet<>(
                skillCatalogService.projectCatalogSkillIds(id));
        Set<String> globalCatalogIds = new LinkedHashSet<>(
                skillCatalogService.globalCatalogSkillIds());

        LinkedHashSet<String> local = new LinkedHashSet<>();
        for (String skillId : configured) {
            if (projectCatalogIds.contains(skillId)
                    || !globalCatalogIds.contains(skillId)) {
                local.add(skillId);
            }
        }
        local.addAll(projectCatalogIds);

        LinkedHashSet<String> global = new LinkedHashSet<>();
        for (String skillId : configured) {
            if (globalCatalogIds.contains(skillId)
                    && !projectCatalogIds.contains(skillId)) {
                global.add(skillId);
            }
        }
        return new ProjectSkillAuthorization(
                List.copyOf(local), List.copyOf(global));
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ProjectSkillAuthorization(
            List<String> localIds,
            List<String> globalIds) {
    }
}
