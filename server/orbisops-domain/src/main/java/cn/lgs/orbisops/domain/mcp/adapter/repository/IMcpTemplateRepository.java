package cn.lgs.orbisops.domain.mcp.adapter.repository;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;

import java.util.List;
import java.util.Optional;

public interface IMcpTemplateRepository {

    List<McpTemplateCatalogEntry> listVisible();

    Optional<McpTemplateCatalogEntry> findVisible(String templateId);

    boolean exists(String templateId);

    McpTemplateCatalogEntry insert(McpTemplateDefinition definition);

    McpTemplateCatalogEntry update(McpTemplateDefinition definition);

    McpTemplateCatalogEntry updateStatus(String templateId, McpTemplateStatus status);
}
