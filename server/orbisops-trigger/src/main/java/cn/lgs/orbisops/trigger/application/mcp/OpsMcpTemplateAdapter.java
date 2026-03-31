package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpTemplateCatalogApplicationService;
import cn.lgs.orbisops.application.mcp.McpTemplatePort;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Thin composition adapter exposing the typed MCP template catalog port. */
@Component
public class OpsMcpTemplateAdapter implements McpTemplatePort {

    private final McpTemplateCatalogApplicationService catalogService;

    public OpsMcpTemplateAdapter(McpTemplateCatalogApplicationService catalogService) {
        if (catalogService == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_CATALOG_SERVICE_REQUIRED");
        }
        this.catalogService = catalogService;
    }

    @Override
    public List<McpTemplateCatalogEntry> listEntries() {
        return catalogService.listEntries();
    }

    @Override
    public McpTemplateCatalogEntry getEntry(String templateId) {
        return catalogService.getEntry(templateId);
    }

    @Override
    public McpTemplateCatalogEntry createDefinition(McpTemplateDefinition definition) {
        return catalogService.createDefinition(definition);
    }

    @Override
    public McpTemplateCatalogEntry updateDefinition(
            String templateId,
            McpTemplateDefinition definition) {
        return catalogService.updateDefinition(templateId, definition);
    }

    @Override
    public McpTemplateCatalogEntry updateStatusDefinition(
            String templateId,
            McpTemplateStatus status) {
        return catalogService.updateStatusDefinition(templateId, status);
    }

    @Override
    public McpTemplateCatalogEntry copyDefinition(
            String templateId,
            Map<String, Object> request) {
        return catalogService.copyDefinition(templateId, request);
    }
}
