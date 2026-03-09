package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectMcpProjectionApplicationService {

    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectWorkspaceProjectionPort workspacePort;

    public ProjectMcpProjectionApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectWorkspaceProjectionPort workspacePort) {
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (workspacePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_PORT_REQUIRED");
        }
        this.catalogService = catalogService;
        this.workspacePort = workspacePort;
    }

    public Map<String, Object> publish(Map<String, Object> source) {
        Map<String, Object> definition = source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
        ProjectMcpDefinition saved = catalogService.save(definition);
        workspacePort.materializeMcp(saved);
        return catalogService.view(saved);
    }
}
