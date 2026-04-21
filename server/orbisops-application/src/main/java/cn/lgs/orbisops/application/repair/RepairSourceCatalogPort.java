package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

import java.util.Optional;

public interface RepairSourceCatalogPort {

    Optional<ProjectService> findService(String projectId, String serviceId);

    Optional<SourceRepository> findRepository(String projectId, String repositoryId);

    Optional<DeploymentRevision> resolveDeployment(String projectId, String environment, String serviceId);
}
