package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionPort;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.trigger.ops.OpsProjectWorkspaceService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Write-side ACL for materializing the remaining workspace compatibility projection. */
@Component
public class OpsProjectWorkspaceProjectionAdapter implements ProjectWorkspaceProjectionPort {

    private final OpsProjectWorkspaceService service;

    public OpsProjectWorkspaceProjectionAdapter(OpsProjectWorkspaceService service) {
        if (service == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_SERVICE_REQUIRED");
        }
        this.service = service;
    }

    @Override
    public void materializeDefinition(ProjectDefinition definition) {
        service.materializeProjectDefinition(definition(definition));
    }

    @Override
    public void materializeResource(ProjectResourceDefinition resource) {
        service.materializeProjectResource(resource(resource));
    }

    @Override
    public void materializeMcp(ProjectMcpDefinition mcp) {
        service.materializeProjectMcp(mcp(mcp));
    }

    private Map<String, Object> definition(ProjectDefinition value) {
        if (value == null) throw new IllegalArgumentException("PROJECT_DEFINITION_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", value.projectId());
        result.put("name", value.name());
        result.put("description", value.description());
        result.put("owner", value.owner());
        result.put("environments", value.environments());
        result.put("knowledgeBaseId", value.knowledgeBaseId());
        result.put("defaultAgentId", value.defaultAgentId());
        result.put("skillIds", value.skillIds());
        result.put("sharedMcpIds", value.sharedMcpIds());
        result.put("enabled", value.enabled());
        result.put("createdAt", time(value.createdAt()));
        result.put("updatedAt", time(value.updatedAt()));
        return result;
    }

    private Map<String, Object> resource(ProjectResourceDefinition value) {
        if (value == null) throw new IllegalArgumentException("PROJECT_RESOURCE_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", value.resourceId());
        result.put("projectId", value.projectId());
        result.put("type", value.type().value());
        result.put("typeName", value.typeName());
        result.put("name", value.name());
        result.put("environment", value.environment());
        result.put("endpoint", value.endpoint());
        result.put("credential", value.credential());
        result.put("status", value.status());
        result.put("schema", value.schema());
        result.put("permission", value.permission());
        result.put("createdAt", time(value.createdAt()));
        result.put("updatedAt", time(value.updatedAt()));
        return result;
    }

    private Map<String, Object> mcp(ProjectMcpDefinition value) {
        if (value == null) throw new IllegalArgumentException("PROJECT_MCP_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mcpId", value.mcpId());
        result.put("toolId", value.mcpId());
        result.put("mcpName", value.mcpName());
        result.put("toolName", value.mcpName());
        result.put("projectId", value.projectId());
        result.put("resourceId", value.resourceId());
        result.put("resourceType", value.resourceType());
        result.put("transportType", value.transportType());
        result.put("templateId", value.templateId());
        result.put("transportConfig", value.transportConfig());
        result.put("allowedActions", value.allowedActions());
        result.put("riskLevel", value.riskLevel().name());
        result.put("readOnly", value.readOnly());
        result.put("permissionPolicy", value.permissionPolicy());
        result.put("requestTimeout", value.requestTimeout());
        result.put("status", value.status().name());
        result.put("createdAt", time(value.createdAt()));
        result.put("updatedAt", time(value.updatedAt()));
        return result;
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
