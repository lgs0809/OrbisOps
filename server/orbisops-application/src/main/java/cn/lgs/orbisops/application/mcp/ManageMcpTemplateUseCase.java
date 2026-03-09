package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionPort;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.mcp.service.McpTemplatePolicy;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Application use case for governed MCP template lifecycle and project-tool generation. */
public final class ManageMcpTemplateUseCase {

    private final McpTemplatePort port;
    private final McpAuditPort auditPort;
    private final McpProjectTemplateGenerationPort generationService;
    private final ProjectWorkspaceProjectionPort projectWorkspacePort;
    private final McpTemplatePolicy policy;

    public ManageMcpTemplateUseCase(
            McpTemplatePort port,
            McpAuditPort auditPort,
            McpProjectTemplateGenerationPort generationService,
            ProjectWorkspaceProjectionPort projectWorkspacePort) {
        this(port, auditPort, generationService, projectWorkspacePort,
                new McpTemplatePolicy());
    }

    ManageMcpTemplateUseCase(
            McpTemplatePort port,
            McpAuditPort auditPort,
            McpProjectTemplateGenerationPort generationService,
            ProjectWorkspaceProjectionPort projectWorkspacePort,
            McpTemplatePolicy policy) {
        if (port == null) throw new IllegalArgumentException("MCP_TEMPLATE_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("MCP_AUDIT_PORT_REQUIRED");
        if (generationService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_TEMPLATE_GENERATION_SERVICE_REQUIRED");
        }
        if (projectWorkspacePort == null) {
            throw new IllegalArgumentException("PROJECT_WORKSPACE_PROJECTION_PORT_REQUIRED");
        }
        if (policy == null) throw new IllegalArgumentException("MCP_TEMPLATE_POLICY_REQUIRED");
        this.port = port;
        this.auditPort = auditPort;
        this.generationService = generationService;
        this.projectWorkspacePort = projectWorkspacePort;
        this.policy = policy;
    }

    public Map<String, Object> create(Map<String, Object> request, String actor) {
        String operator = required(actor, "MCP_ACTOR_REQUIRED");
        Map<String, Object> command = safe(request);
        command.put("createBy", operator);
        command.remove("actor");
        McpTemplateDefinition definition = policy.create(command);
        McpTemplateCatalogEntry created = port.createDefinition(definition);
        Map<String, Object> result = view(created);
        auditPort.record("", "mcp-template", "create", definition.templateId(), null,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> update(String templateId, Map<String, Object> request, String actor) {
        String operator = required(actor, "MCP_ACTOR_REQUIRED");
        McpTemplateCatalogEntry current = port.getEntry(templateId);
        Map<String, Object> command = safe(request);
        command.remove("createBy");
        command.remove("actor");
        McpTemplateDefinition definition = policy.update(
                policy.toMap(current.definition()), command);
        McpTemplateCatalogEntry updated = port.updateDefinition(templateId, definition);
        Map<String, Object> before = view(current);
        Map<String, Object> result = view(updated);
        auditPort.record("", "mcp-template", "update", templateId, before,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> updateStatus(String templateId, String targetStatus, String actor) {
        String operator = required(actor, "MCP_ACTOR_REQUIRED");
        McpTemplateCatalogEntry current = port.getEntry(templateId);
        McpTemplateStatus status = policy.transition(
                current.definition().status().name(), targetStatus);
        McpTemplateCatalogEntry updated = port.updateStatusDefinition(templateId, status);
        Map<String, Object> before = view(current);
        Map<String, Object> result = view(updated);
        auditPort.record("", "mcp-template", "status", templateId, before,
                audited(result, operator));
        return result;
    }

    public Map<String, Object> copy(String templateId, Map<String, Object> request, String actor) {
        String operator = required(actor, "MCP_ACTOR_REQUIRED");
        McpTemplateCatalogEntry source = port.getEntry(templateId);
        Map<String, Object> command = safe(request);
        command.put("createBy", operator);
        command.remove("actor");
        Object requestedIdValue = command.containsKey("templateId")
                ? command.get("templateId")
                : command.get("mcpTemplateId");
        String requestedId = requestedIdValue == null ? "" : String.valueOf(requestedIdValue).trim();
        McpTemplateCatalogEntry copied;
        if (requestedId.isBlank()) {
            copied = port.copyDefinition(templateId, command);
        } else {
            Map<String, Object> candidate = new LinkedHashMap<>(
                    policy.toMap(source.definition()));
            candidate.putAll(command);
            candidate.put("templateId", requestedId);
            candidate.remove("mcpTemplateId");
            McpTemplateDefinition definition = policy.create(candidate);
            copied = port.createDefinition(definition);
        }
        Map<String, Object> result = view(copied);
        auditPort.record("", "mcp-template", "copy", templateId,
                Map.of("sourceTemplateId", templateId), audited(result, operator));
        return result;
    }

    public Map<String, Object> generateProjectTool(String projectId,
                                                   String templateId,
                                                   Map<String, Object> request,
                                                   String actor) {
        String operator = required(actor, "MCP_ACTOR_REQUIRED");
        McpTemplateCatalogEntry template = port.getEntry(templateId);
        Map<String, Object> command = safe(request);
        command.put("actor", operator);
        McpProjectToolGenerationOutcome generated = generationService.generate(
                projectId, template.definition(), command);
        ProjectMcpDefinition definition = generated.definition();
        projectWorkspacePort.materializeMcp(definition);
        Map<String, Object> result = generated.view();
        auditPort.record(projectId, "project-tool", "generate-from-template",
                definition.mcpId(),
                Map.of("templateId", templateId), audited(result, operator));
        return result;
    }

    private Map<String, Object> view(McpTemplateCatalogEntry entry) {
        return McpTemplateCatalogView.of(entry);
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
    }

    private Map<String, Object> audited(Object result, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("actor", actor);
        return payload;
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
