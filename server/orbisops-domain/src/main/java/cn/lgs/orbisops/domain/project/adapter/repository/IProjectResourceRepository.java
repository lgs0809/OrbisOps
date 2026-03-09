package cn.lgs.orbisops.domain.project.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.util.List;
import java.util.Optional;

public interface IProjectResourceRepository {

    List<ProjectResourceDefinition> list(String projectId);

    Optional<ProjectResourceDefinition> find(String projectId, String resourceId);

    ProjectResourceDefinition save(ProjectResourceDefinition resource);
}
