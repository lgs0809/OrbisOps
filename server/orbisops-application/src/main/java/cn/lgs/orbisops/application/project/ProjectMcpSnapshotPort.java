package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;

import java.util.List;

/** Read-only project MCP snapshot used to rebuild the runtime directory. */
public interface ProjectMcpSnapshotPort {

    List<ProjectMcpDefinition> listAll();
}
