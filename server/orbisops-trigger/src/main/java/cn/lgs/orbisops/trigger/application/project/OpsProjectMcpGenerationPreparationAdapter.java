package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationPort;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationRequest;
import org.springframework.stereotype.Component;

@Component
public class OpsProjectMcpGenerationPreparationAdapter
        implements ProjectMcpGenerationPreparationPort {

    private final OpsProjectMcpGenerationPreparationFactory preparationFactory;

    public OpsProjectMcpGenerationPreparationAdapter(
            OpsProjectMcpGenerationPreparationFactory preparationFactory) {
        if (preparationFactory == null) {
            throw new IllegalArgumentException("PROJECT_MCP_PREPARATION_FACTORY_REQUIRED");
        }
        this.preparationFactory = preparationFactory;
    }

    @Override
    public ProjectMcpGenerationPreparation prepare(
            ProjectMcpGenerationPreparationRequest request) {
        return preparationFactory.prepare(request);
    }
}
