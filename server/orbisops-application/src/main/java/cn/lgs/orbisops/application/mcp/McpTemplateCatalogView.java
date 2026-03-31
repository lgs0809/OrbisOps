package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compatibility projection for typed MCP template catalog facts. */
public final class McpTemplateCatalogView {

    private McpTemplateCatalogView() {
    }

    public static Map<String, Object> of(McpTemplateCatalogEntry entry) {
        if (entry == null) throw new IllegalArgumentException("MCP_TEMPLATE_ENTRY_REQUIRED");
        McpTemplateDefinition definition = entry.definition();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.catalogId());
        result.put("templateId", definition.templateId());
        result.put("mcpTemplateId", definition.templateId());
        result.put("templateName", definition.templateName());
        result.put("name", definition.templateName());
        result.put("resourceType", definition.resourceType());
        result.put("transportType", definition.transportType());
        result.put("defaultTransportConfig", definition.defaultTransportConfig());
        result.put("supportedActions", definition.supportedActions());
        result.put("riskLevel", definition.riskLevel());
        result.put("readOnly", definition.readOnly());
        result.put("description", definition.description());
        result.put("status", definition.status().name());
        result.put("createBy", definition.createBy());
        result.put("createTime", time(entry.createdAt()));
        result.put("updateTime", time(entry.updatedAt()));
        return Map.copyOf(result);
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
