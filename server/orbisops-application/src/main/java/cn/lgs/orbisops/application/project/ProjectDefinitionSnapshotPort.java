package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;

import java.util.List;

/** Read-only project-definition snapshot for legacy aggregate projections. */
public interface ProjectDefinitionSnapshotPort {

    List<ProjectDefinition> listEnabled();
}
