package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;

import java.util.List;
import java.util.Optional;

public interface ExecutionResourceProjectPort {

    boolean exists(String projectId);

    List<String> environments(String projectId);

    Optional<ExecutionSourceResource> findSourceResource(String projectId, String resourceId);
}
