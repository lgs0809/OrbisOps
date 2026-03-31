package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Map;

/** Query projection for Project MCP instances generated from a template. */
public interface McpTemplateProjectUsagePort {

    List<Map<String, Object>> usages(String templateId);
}
