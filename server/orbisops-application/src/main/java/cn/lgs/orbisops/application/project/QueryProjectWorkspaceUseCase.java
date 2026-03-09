package cn.lgs.orbisops.application.project;

import java.util.List;
import java.util.Map;

public final class QueryProjectWorkspaceUseCase {

    private final ProjectWorkspaceQueryPort port;
    private final ProjectMemberApplicationService memberService;
    private final ProjectMcpCatalogApplicationService mcpCatalogService;

    public QueryProjectWorkspaceUseCase(
            ProjectWorkspaceQueryPort port,
            ProjectMemberApplicationService memberService,
            ProjectMcpCatalogApplicationService mcpCatalogService) {
        if (port == null) throw new IllegalArgumentException("PROJECT_WORKSPACE_QUERY_PORT_REQUIRED");
        if (memberService == null) throw new IllegalArgumentException("PROJECT_MEMBER_SERVICE_REQUIRED");
        if (mcpCatalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        this.port = port;
        this.memberService = memberService;
        this.mcpCatalogService = mcpCatalogService;
    }

    public Map<String, Object> snapshot() { return port.snapshot(); }
    public List<Map<String, Object>> templates() { return port.templates(); }
    public Map<String, Object> detail(String projectId) { return port.detail(projectId); }

    public List<Map<String, Object>> members(String projectId) {
        String id = required(projectId);
        requireProject(id);
        return memberService.list(id);
    }

    public List<Map<String, Object>> projectTools(String projectId) {
        String id = required(projectId);
        requireProject(id);
        return mcpCatalogService.list(id).stream()
                .map(mcpCatalogService::view)
                .toList();
    }

    private void requireProject(String projectId) {
        Map<String, Object> project = port.detail(projectId);
        if (project == null || project.isEmpty()) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
    }

    private String required(String projectId) {
        String normalized = projectId == null ? "" : projectId.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("PROJECT_ID_REQUIRED");
        }
        return normalized;
    }
}
