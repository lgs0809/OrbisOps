package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

/** Typed runtime descriptor passed from Application to the Trigger runtime ACL. */
public record ProjectMcpRuntimeDescriptor(
        ProjectMcpDefinition mcp,
        ProjectResourceDefinition resource) {

    public ProjectMcpRuntimeDescriptor {
        if (mcp == null) {
            throw new IllegalArgumentException("PROJECT_MCP_DEFINITION_REQUIRED");
        }
        if ("external_mcp".equals(mcp.resourceType())) {
            resource = null;
        } else {
            if (resource == null) {
                throw new IllegalArgumentException("PROJECT_MCP_RESOURCE_REQUIRED");
            }
            if (!mcp.projectId().equals(resource.projectId())
                    || !mcp.resourceId().equals(resource.resourceId())) {
                throw new IllegalArgumentException("PROJECT_MCP_RESOURCE_BINDING_MISMATCH");
            }
        }
    }

    public boolean external() {
        return "external_mcp".equals(mcp.resourceType());
    }
}
