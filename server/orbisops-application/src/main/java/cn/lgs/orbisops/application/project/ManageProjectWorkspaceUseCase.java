package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Process manager for project creation and project-scoped configuration changes. */
public final class ManageProjectWorkspaceUseCase {

    private final ProjectWorkspaceQueryPort workspaceQueries;
    private final ProjectWorkspaceProjectionPort workspaceProjection;
    private final ProjectDefaultAgentPort defaultAgentPort;
    private final ProjectAuditPort auditPort;
    private final ProjectMemberApplicationService memberService;
    private final ProjectDefinitionApplicationService definitionService;
    private final ProjectResourceApplicationService resourceService;
    private final ProjectMcpGenerationApplicationService mcpGenerationService;
    private final ProjectMcpManagementApplicationService mcpManagementService;
    private final ProjectMcpCatalogApplicationService mcpCatalogService;

    public ManageProjectWorkspaceUseCase(ProjectWorkspacePort workspacePort,
                                         ProjectDefaultAgentPort defaultAgentPort,
                                         ProjectAuditPort auditPort,
                                         ProjectMemberApplicationService memberService,
                                         ProjectDefinitionApplicationService definitionService,
                                         ProjectResourceApplicationService resourceService,
                                         ProjectMcpGenerationApplicationService mcpGenerationService,
                                         ProjectMcpManagementApplicationService mcpManagementService,
                                         ProjectMcpCatalogApplicationService mcpCatalogService) {
        this(workspacePort, workspacePort, defaultAgentPort, auditPort, memberService,
                definitionService, resourceService, mcpGenerationService,
                mcpManagementService, mcpCatalogService);
    }

    public ManageProjectWorkspaceUseCase(ProjectWorkspaceQueryPort workspaceQueries,
                                         ProjectWorkspaceProjectionPort workspaceProjection,
                                         ProjectDefaultAgentPort defaultAgentPort,
                                         ProjectAuditPort auditPort,
                                         ProjectMemberApplicationService memberService,
                                         ProjectDefinitionApplicationService definitionService,
                                         ProjectResourceApplicationService resourceService,
                                         ProjectMcpGenerationApplicationService mcpGenerationService,
                                         ProjectMcpManagementApplicationService mcpManagementService,
                                         ProjectMcpCatalogApplicationService mcpCatalogService) {
        if (workspaceQueries == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_QUERY_PORT_REQUIRED");
        }
        if (workspaceProjection == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_PORT_REQUIRED");
        }
        if (defaultAgentPort == null) throw new IllegalArgumentException("PROJECT_DEFAULT_AGENT_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("PROJECT_AUDIT_PORT_REQUIRED");
        if (memberService == null) throw new IllegalArgumentException("PROJECT_MEMBER_SERVICE_REQUIRED");
        if (definitionService == null) throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        if (resourceService == null) throw new IllegalArgumentException("PROJECT_RESOURCE_SERVICE_REQUIRED");
        if (mcpGenerationService == null) throw new IllegalArgumentException("PROJECT_MCP_GENERATION_SERVICE_REQUIRED");
        if (mcpManagementService == null) throw new IllegalArgumentException("PROJECT_MCP_MANAGEMENT_SERVICE_REQUIRED");
        if (mcpCatalogService == null) throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        this.workspaceQueries = workspaceQueries;
        this.workspaceProjection = workspaceProjection;
        this.defaultAgentPort = defaultAgentPort;
        this.auditPort = auditPort;
        this.memberService = memberService;
        this.definitionService = definitionService;
        this.resourceService = resourceService;
        this.mcpGenerationService = mcpGenerationService;
        this.mcpManagementService = mcpManagementService;
        this.mcpCatalogService = mcpCatalogService;
    }

    public Map<String, Object> createProject(Map<String, Object> request, String actor) {
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        ProjectDefinition project = definitionService.createDefinition(command.payload());
        workspaceProjection.materializeDefinition(project);
        ProjectDefaultAgentResult agent = defaultAgentPort.ensure(project.projectId(), project.name());
        ProjectDefinition definition = definitionService.assignDefaultAgent(project.projectId(), agent.agentId());
        workspaceProjection.materializeDefinition(definition);
        Map<String, Object> result = aggregate(project.projectId());
        auditPort.record(project.projectId(), "project", "create", project.projectId(), null,
                audited(result, command.actor()));
        return result;
    }

    public Map<String, Object> ensureDefaultAgent(String projectId, String actor) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String operator = required(actor, "PROJECT_ACTOR_REQUIRED");
        ProjectDefinition project = definitionService.requireDefinition(id);
        Map<String, Object> before = workspaceQueries.detail(id);
        ProjectDefaultAgentResult agent = defaultAgentPort.ensure(id, project.name());
        ProjectDefinition definition = definitionService.assignDefaultAgent(id, agent.agentId());
        workspaceProjection.materializeDefinition(definition);
        Map<String, Object> result = aggregate(id);
        auditPort.record(id, "project", "ensure-default-agent", id, before,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> updateProject(Map<String, Object> request, String actor) {
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        String projectId = command.projectId();
        Map<String, Object> before = workspaceQueries.detail(projectId);
        if (before == null || before.isEmpty()) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
        ProjectDefinition definition = definitionService.updateDefinition(command.payload());
        workspaceProjection.materializeDefinition(definition);
        Map<String, Object> result = aggregate(projectId);
        auditPort.record(projectId, "project", "update", projectId, before,
                audited(result, command.actor()));
        return result;
    }

    public List<Map<String, Object>> replaceMembers(String projectId,
                                                     List<Map<String, Object>> members,
                                                     String actor) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String operator = required(actor, "PROJECT_ACTOR_REQUIRED");
        definitionService.requireDefinition(id);
        List<Map<String, Object>> result = memberService.replace(
                id, members == null ? List.of() : members, operator);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("projectId", id);
        after.put("members", result);
        after.put("actor", operator);
        auditPort.record(id, "project-member", "replace", id, null, after);
        return result;
    }

    public Map<String, Object> addResource(Map<String, Object> request, String actor) {
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        ProjectResourceDefinition resource = resourceService.createResource(command.payload());
        workspaceProjection.materializeResource(resource);
        Map<String, Object> result = aggregate(resource.projectId());
        auditPort.record(resource.projectId(), "project-resource", "create", resource.resourceId(), null,
                audited(result, command.actor()));
        return result;
    }

    public Map<String, Object> updateResource(Map<String, Object> request, String actor) {
        return mutateResource("update", request, actor, resourceService::updateResource);
    }

    public Map<String, Object> updatePermission(Map<String, Object> request, String actor) {
        return mutateResource("permission", request, actor, resourceService::updatePermissionResource);
    }

    public Map<String, Object> generateMcp(Map<String, Object> request, String actor) {
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        ProjectMcpDefinition mcp = mcpGenerationService.generateDefinition(command.payload());
        workspaceProjection.materializeMcp(mcp);
        Map<String, Object> result = aggregate(mcp.projectId());
        auditPort.record(mcp.projectId(), "project-mcp", "generate", mcp.mcpId(), null,
                audited(result, command.actor()));
        return result;
    }

    public Map<String, Object> updateProjectTool(String projectId,
                                                 String toolId,
                                                 Map<String, Object> request,
                                                 String actor) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String target = required(toolId, "PROJECT_TOOL_ID_REQUIRED");
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        Map<String, Object> before = mcpCatalogService.find(id, target)
                .map(mcpCatalogService::view)
                .orElse(Map.of());
        ProjectMcpDefinition updated = mcpManagementService.updateDefinition(
                id, target, command.payload());
        Map<String, Object> result = mcpCatalogService.view(updated);
        auditPort.record(id, "project-tool", "update", target, before,
                audited(result, command.actor()));
        return result;
    }

    public Map<String, Object> updateProjectToolStatus(String projectId,
                                                       String toolId,
                                                       String status,
                                                       String actor) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String target = required(toolId, "PROJECT_TOOL_ID_REQUIRED");
        String operator = required(actor, "PROJECT_ACTOR_REQUIRED");
        Map<String, Object> before = mcpCatalogService.find(id, target)
                .map(mcpCatalogService::view)
                .orElse(Map.of());
        ProjectMcpDefinition updated = mcpManagementService.updateStatusDefinition(
                id, target, text(status, "DISABLED"));
        Map<String, Object> result = mcpCatalogService.view(updated);
        auditPort.record(id, "project-tool", "status", target, before,
                audited(result, operator));
        return result;
    }

    private Map<String, Object> mutateResource(
            String action,
            Map<String, Object> request,
            String actor,
            java.util.function.Function<Map<String, Object>, ProjectResourceDefinition> operation) {
        ProjectWorkspaceMutationCommand command = ProjectWorkspaceMutationCommand.from(request, actor);
        String projectId = command.projectId();
        String resourceId = command.resourceId();
        Map<String, Object> before = resourceService.findView(projectId, resourceId);
        ProjectResourceDefinition resource = operation.apply(command.payload());
        workspaceProjection.materializeResource(resource);
        Map<String, Object> result = aggregate(projectId);
        auditPort.record(projectId, "project-resource", action, resourceId, before,
                audited(result, command.actor()));
        return result;
    }

    private Map<String, Object> aggregate(String projectId) {
        Map<String, Object> result = workspaceQueries.detail(projectId);
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("PROJECT_AGGREGATE_MATERIALIZATION_FAILED:" + projectId);
        }
        return result;
    }

    private Map<String, Object> audited(Object result, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("actor", actor);
        return payload;
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
