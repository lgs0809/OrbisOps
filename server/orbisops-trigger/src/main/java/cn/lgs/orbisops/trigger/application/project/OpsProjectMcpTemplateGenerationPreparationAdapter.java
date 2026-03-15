package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparationPort;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparationRequest;
import org.springframework.stereotype.Component;

@Component
public class OpsProjectMcpTemplateGenerationPreparationAdapter
        implements ProjectMcpTemplateGenerationPreparationPort {

    private final OpsProjectMcpGenerationPreparationFactory preparationFactory;

    public OpsProjectMcpTemplateGenerationPreparationAdapter(
            OpsProjectMcpGenerationPreparationFactory preparationFactory) {
        if (preparationFactory == null) {
            throw new IllegalArgumentException("PROJECT_MCP_PREPARATION_FACTORY_REQUIRED");
        }
        this.preparationFactory = preparationFactory;
    }

    @Override
    public ProjectMcpTemplateGenerationPreparation prepare(
            ProjectMcpTemplateGenerationPreparationRequest request) {
        return preparationFactory.prepareTemplate(request);
    }
}
