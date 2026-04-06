package cn.lgs.orbisops.domain.execution.adapter.repository;

import cn.lgs.orbisops.domain.execution.model.ExecutionResource;

import java.util.List;
import java.util.Optional;

public interface IExecutionResourceRepository {

    List<ExecutionResource> findAllVisible();

    Optional<ExecutionResource> find(String projectId, String resourceId);

    boolean existsWorkerResourceOutsideProject(String workerId, String resourceId, String projectId);

    ExecutionResource save(ExecutionResource resource);
}
