package cn.lgs.orbisops.domain.project.adapter.repository;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;

import java.util.List;
import java.util.Optional;

public interface IProjectMcpRepository {

    List<ProjectMcpDefinition> listAll();

    List<ProjectMcpDefinition> list(String projectId);

    List<ProjectMcpDefinition> listByTemplate(String templateId);

    Optional<ProjectMcpDefinition> find(String projectId, String mcpId);

    ProjectMcpDefinition save(ProjectMcpDefinition definition);
}
