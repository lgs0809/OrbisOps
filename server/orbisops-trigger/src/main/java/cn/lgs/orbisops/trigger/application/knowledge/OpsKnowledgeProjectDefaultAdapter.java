package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeProjectDefaultPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeProjectExistencePort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import org.springframework.stereotype.Component;

@Component
public class OpsKnowledgeProjectDefaultAdapter
        implements KnowledgeProjectDefaultPort, KnowledgeProjectExistencePort {

    private final ProjectDefinitionApplicationService projectDefinitionService;

    public OpsKnowledgeProjectDefaultAdapter(
            ProjectDefinitionApplicationService projectDefinitionService) {
        this.projectDefinitionService = projectDefinitionService;
    }

    @Override
    public String defaultKnowledgeBaseId(String projectId) {
        return projectDefinitionService.defaultKnowledgeBaseId(projectId);
    }

    @Override
    public boolean exists(String projectId) {
        return projectDefinitionService.exists(projectId);
    }
}
