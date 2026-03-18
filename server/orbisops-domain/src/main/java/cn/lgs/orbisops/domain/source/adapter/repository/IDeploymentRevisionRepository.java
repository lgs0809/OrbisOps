package cn.lgs.orbisops.domain.source.adapter.repository;

import cn.lgs.orbisops.domain.source.model.DeploymentRevision;

import java.util.List;
import java.util.Optional;

public interface IDeploymentRevisionRepository {

    boolean available();

    DeploymentRevision save(DeploymentRevision deployment);

    List<DeploymentRevision> list(String projectId, String environment);

    Optional<DeploymentRevision> resolve(String projectId, String environment, String serviceName);
}
