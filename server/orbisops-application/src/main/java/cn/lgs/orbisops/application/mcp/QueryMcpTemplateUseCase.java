package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Query projection over typed MCP template catalog entries. */
public final class QueryMcpTemplateUseCase {

    private final McpTemplatePort port;
    private final McpTemplateProjectUsagePort projectMcpCatalogService;

    public QueryMcpTemplateUseCase(
            McpTemplatePort port,
            McpTemplateProjectUsagePort projectMcpCatalogService) {
        if (port == null) throw new IllegalArgumentException("MCP_TEMPLATE_PORT_REQUIRED");
        if (projectMcpCatalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        this.port = port;
        this.projectMcpCatalogService = projectMcpCatalogService;
    }

    public List<Map<String, Object>> list() {
        return port.listEntries().stream()
                .map(this::enrich)
                .toList();
    }

    public Map<String, Object> get(String templateId) {
        return enrich(port.getEntry(templateId));
    }

    public List<Map<String, Object>> generatedTools(String templateId) {
        port.getEntry(templateId);
        return toolViews(templateId);
    }

    private Map<String, Object> enrich(McpTemplateCatalogEntry template) {
        Map<String, Object> view = new LinkedHashMap<>(McpTemplateCatalogView.of(template));
        String templateId = template.definition().templateId();
        List<Map<String, Object>> tools = toolViews(templateId);
        view.put("projectCount", tools.stream()
                .map(item -> String.valueOf(item.getOrDefault("projectId", "")).trim())
                .filter(projectId -> !projectId.isBlank())
                .distinct()
                .count());
        view.put("generatedToolCount", tools.size());
        view.put("generatedTools", tools);
        return Map.copyOf(view);
    }

    private List<Map<String, Object>> toolViews(String templateId) {
        return projectMcpCatalogService.usages(templateId);
    }
}
