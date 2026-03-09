package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import java.util.Map;

/** Customer/Supplier boundary for generating a Project MCP from an MCP template. */
public interface McpProjectTemplateGenerationPort {

    McpProjectToolGenerationOutcome generate(
            String projectId,
            McpTemplateDefinition template,
            Map<String, Object> request);
}
