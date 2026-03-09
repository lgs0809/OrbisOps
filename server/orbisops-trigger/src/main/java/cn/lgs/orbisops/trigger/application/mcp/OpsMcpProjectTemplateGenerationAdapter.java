package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpProjectTemplateGenerationPort;
import cn.lgs.orbisops.application.mcp.McpProjectToolGenerationOutcome;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationApplicationService;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

/** ACL invoking the Project Workspace template-generation use case. */
@Component
public final class OpsMcpProjectTemplateGenerationAdapter
        implements McpProjectTemplateGenerationPort {

    private final ProjectMcpTemplateGenerationApplicationService generation;

    public OpsMcpProjectTemplateGenerationAdapter(
            ProjectMcpTemplateGenerationApplicationService generation) {
        if (generation == null) throw new IllegalArgumentException("PROJECT_MCP_GENERATION_SERVICE_REQUIRED");
        this.generation = generation;
    }

    @Override
    public McpProjectToolGenerationOutcome generate(
            String projectId,
            McpTemplateDefinition template,
            Map<String, Object> request) {
        ProjectMcpDefinition definition = generation.generateDefinition(projectId, template, request);
        return new McpProjectToolGenerationOutcome(definition, generation.view(definition));
    }
}
