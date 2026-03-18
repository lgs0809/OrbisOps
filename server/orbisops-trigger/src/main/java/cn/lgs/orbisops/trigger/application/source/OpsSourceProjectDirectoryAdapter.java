package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.source.SourceProjectDirectoryPort;
import org.springframework.stereotype.Component;

@Component
public class OpsSourceProjectDirectoryAdapter implements SourceProjectDirectoryPort {

    private final ProjectDefinitionApplicationService projects;

    public OpsSourceProjectDirectoryAdapter(ProjectDefinitionApplicationService projects) {
        this.projects = projects;
    }

    @Override
    public boolean exists(String projectId) {
        return projects.exists(projectId);
    }
}
