package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.util.LinkedHashSet;
import java.util.List;

public final class ProjectMcpAuthorizationApplicationService {

    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectDefinitionApplicationService definitionService;

    public ProjectMcpAuthorizationApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectDefinitionApplicationService definitionService) {
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (definitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        this.catalogService = catalogService;
        this.definitionService = definitionService;
    }

    public List<String> enabledIds(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        ProjectDefinition project = definitionService.findDefinition(id)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + id));
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        catalogService.list(id).stream()
                .filter(definition -> ProjectMcpStatus.ENABLED == definition.status())
                .map(definition -> definition.mcpId())
                .filter(value -> value != null && !value.isBlank())
                .forEach(ids::add);
        ids.addAll(project.sharedMcpIds());
        return List.copyOf(ids);
    }

    public boolean allows(String projectId, String mcpId) {
        String project = text(projectId);
        String mcp = text(mcpId);
        return !project.isBlank()
                && !mcp.isBlank()
                && enabledIds(project).contains(mcp);
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
