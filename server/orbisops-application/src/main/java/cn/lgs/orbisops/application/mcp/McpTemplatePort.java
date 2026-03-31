package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;

import java.util.List;
import java.util.Map;

/** Typed boundary for MCP template catalog lifecycle operations. */
public interface McpTemplatePort {

    List<McpTemplateCatalogEntry> listEntries();

    McpTemplateCatalogEntry getEntry(String templateId);

    McpTemplateCatalogEntry createDefinition(McpTemplateDefinition definition);

    McpTemplateCatalogEntry updateDefinition(String templateId, McpTemplateDefinition definition);

    McpTemplateCatalogEntry updateStatusDefinition(String templateId, McpTemplateStatus status);

    McpTemplateCatalogEntry copyDefinition(String templateId, Map<String, Object> request);
}
