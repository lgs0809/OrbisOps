package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectMcpTemplateGenerationPreparationRequest(
        String projectId,
        String resourceId,
        String mcpId,
        String profile,
        ProjectResourceDefinition resource,
        McpTemplateDefinition template,
        Map<String, Object> command
) {

    public ProjectMcpTemplateGenerationPreparationRequest {
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        resourceId = required(resourceId, "PROJECT_RESOURCE_ID_REQUIRED");
        mcpId = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        profile = required(profile, "PROJECT_MCP_PROFILE_REQUIRED");
        if (resource == null) throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        if (!projectId.equals(resource.projectId()) || !resourceId.equals(resource.resourceId())) {
            throw new IllegalArgumentException("PROJECT_MCP_RESOURCE_BINDING_MISMATCH");
        }
        if (template == null) throw new IllegalArgumentException("PROJECT_MCP_TEMPLATE_REQUIRED");
        command = copy(command);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String input, String reasonCode) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
