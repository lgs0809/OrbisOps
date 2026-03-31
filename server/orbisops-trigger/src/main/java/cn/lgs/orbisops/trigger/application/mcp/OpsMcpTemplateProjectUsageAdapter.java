package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpTemplateProjectUsagePort;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Query ACL translating Project MCP definitions into template usage views. */
@Component
public final class OpsMcpTemplateProjectUsageAdapter implements McpTemplateProjectUsagePort {

    private final ProjectMcpCatalogApplicationService catalog;

    public OpsMcpTemplateProjectUsageAdapter(ProjectMcpCatalogApplicationService catalog) {
        if (catalog == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        this.catalog = catalog;
    }

    @Override
    public List<Map<String, Object>> usages(String templateId) {
        return catalog.listByTemplate(templateId).stream()
                .map(catalog::view)
                .toList();
    }
}
