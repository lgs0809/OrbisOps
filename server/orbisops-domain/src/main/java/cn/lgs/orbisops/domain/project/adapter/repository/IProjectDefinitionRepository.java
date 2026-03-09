package cn.lgs.orbisops.domain.project.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.util.List;
import java.util.Optional;

public interface IProjectDefinitionRepository {

    List<ProjectDefinition> listEnabled();

    Optional<ProjectDefinition> find(String projectId);

    boolean exists(String projectId);

    ProjectDefinition save(ProjectDefinition definition);
}
