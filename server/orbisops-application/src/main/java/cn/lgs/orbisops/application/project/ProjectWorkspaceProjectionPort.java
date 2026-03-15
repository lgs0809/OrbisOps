package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

/** Write-side materialization of the remaining workspace compatibility projection. */
public interface ProjectWorkspaceProjectionPort {

    void materializeDefinition(ProjectDefinition definition);

    void materializeResource(ProjectResourceDefinition resource);

    void materializeMcp(ProjectMcpDefinition mcp);
}
