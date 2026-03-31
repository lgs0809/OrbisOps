package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpProjectDirectoryPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import org.springframework.stereotype.Component;

/** ACL exposing only Project existence to MCP Governance. */
@Component
public final class OpsMcpProjectDirectoryAdapter implements McpProjectDirectoryPort {

    private final ProjectDefinitionApplicationService projects;

    public OpsMcpProjectDirectoryAdapter(ProjectDefinitionApplicationService projects) {
        if (projects == null) throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        this.projects = projects;
    }

    @Override
    public boolean exists(String projectId) {
        return projects.exists(projectId);
    }
}
