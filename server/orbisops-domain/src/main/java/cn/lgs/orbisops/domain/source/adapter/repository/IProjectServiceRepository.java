package cn.lgs.orbisops.domain.source.adapter.repository;

import cn.lgs.orbisops.domain.source.model.ProjectService;

import java.util.List;
import java.util.Optional;

public interface IProjectServiceRepository {

    boolean available();

    ProjectService saveService(ProjectService service);

    Optional<ProjectService> findService(String projectId, String serviceId);

    List<ProjectService> listServices(String projectId);
}
