package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairSourceCatalogPort;
import cn.lgs.orbisops.application.source.ProjectServiceApplicationService;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OpsRepairSourceCatalogAdapter implements RepairSourceCatalogPort {

    private final ProjectServiceApplicationService services;
    private final SourceRepositoryApplicationService repositories;

    public OpsRepairSourceCatalogAdapter(
            ProjectServiceApplicationService services,
            SourceRepositoryApplicationService repositories) {
        this.services = services;
        this.repositories = repositories;
    }

    @Override
    public Optional<ProjectService> findService(String projectId, String serviceId) {
        return services.find(projectId, serviceId);
    }

    @Override
    public Optional<SourceRepository> findRepository(String projectId, String repositoryId) {
        return repositories.find(projectId, repositoryId);
    }

    @Override
    public Optional<DeploymentRevision> resolveDeployment(
            String projectId,
            String environment,
            String serviceId) {
        return repositories.resolveDeployment(projectId, environment, serviceId);
    }
}
