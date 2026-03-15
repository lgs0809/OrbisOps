package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectAccessPort;
import cn.lgs.orbisops.application.project.ProjectCatalogEntry;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Project access facts backed by the Project Definition application boundary. */
@Component
public class OpsProjectAccessAdapter implements ProjectAccessPort {

    private final ProjectDefinitionApplicationService projectDefinitionService;

    public OpsProjectAccessAdapter(
            ProjectDefinitionApplicationService projectDefinitionService) {
        if (projectDefinitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        this.projectDefinitionService = projectDefinitionService;
    }

    @Override
    public boolean exists(String projectId) {
        return projectDefinitionService.exists(projectId);
    }

    @Override
    public boolean owner(String projectId, String username, String userId) {
        return projectDefinitionService.owner(projectId, username, userId);
    }

    @Override
    public List<ProjectCatalogEntry> publicCatalog() {
        return projectDefinitionService.listEnabledDefinitions().stream()
                .map(ProjectCatalogEntry::from)
                .toList();
    }
}
