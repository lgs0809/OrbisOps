package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed generated Project MCP plus its compatibility projection. */
public record McpProjectToolGenerationOutcome(
        ProjectMcpDefinition definition,
        Map<String, Object> view
) {

    public McpProjectToolGenerationOutcome {
        if (definition == null) throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        view = view == null || view.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(view));
    }
}
