package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.util.List;

/** Read-only resource snapshot for the legacy aggregate projection. */
public interface ProjectResourceSnapshotPort {

    List<ProjectResourceDefinition> listAll();
}
