package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

public record ProjectMcpGenerationPreparationRequest(
        String projectId,
        String resourceId,
        String mcpId,
        String profile,
        ProjectResourceDefinition resource
) {

    public ProjectMcpGenerationPreparationRequest {
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        resourceId = required(resourceId, "PROJECT_RESOURCE_ID_REQUIRED");
        mcpId = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        profile = required(profile, "PROJECT_MCP_PROFILE_REQUIRED");
        if (resource == null) throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        if (!projectId.equals(resource.projectId()) || !resourceId.equals(resource.resourceId())) {
            throw new IllegalArgumentException("PROJECT_MCP_RESOURCE_BINDING_MISMATCH");
        }
    }

    private static String required(String input, String reasonCode) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
