package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ProjectMcpTemplateGenerationApplicationService {

    private final ProjectResourceApplicationService resourceService;
    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectMcpTemplateGenerationPreparationPort preparationPort;

    public ProjectMcpTemplateGenerationApplicationService(
            ProjectResourceApplicationService resourceService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectMcpTemplateGenerationPreparationPort preparationPort) {
        if (resourceService == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_SERVICE_REQUIRED");
        }
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (preparationPort == null) {
            throw new IllegalArgumentException("PROJECT_MCP_TEMPLATE_PREPARATION_PORT_REQUIRED");
        }
        this.resourceService = resourceService;
        this.catalogService = catalogService;
        this.preparationPort = preparationPort;
    }

    public Map<String, Object> view(ProjectMcpDefinition definition) {
        return catalogService.view(definition);
    }

    public ProjectMcpDefinition generateDefinition(
            String projectId,
            McpTemplateDefinition template,
            Map<String, Object> request) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        if (template == null) throw new IllegalArgumentException("PROJECT_MCP_TEMPLATE_REQUIRED");
        String templateId = required(template.templateId(), "PROJECT_MCP_TEMPLATE_ID_REQUIRED");
        Map<String, Object> command = safe(request);
        String resourceId = required(command.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED");
        ProjectResourceDefinition resource = resourceService.requireResource(id, resourceId);
        String actualResourceType = resource.type().value();
        String templateResourceType = ProjectResourceType.from(
                text(template.resourceType(), actualResourceType)).value();
        if (!templateResourceType.equals(actualResourceType)) {
            throw new IllegalArgumentException(
                    "模板资源类型与项目资源不匹配："
                            + templateResourceType + " != " + actualResourceType);
        }
        String profile = normalizeId(text(command.get("profile"), "readonly"));
        String defaultMcpId = id + "-" + actualResourceType + "-"
                + resource.environment() + "-" + profile + "-mcp";
        String mcpId = normalizeId(text(command.get("toolId"),
                text(command.get("mcpId"), defaultMcpId)));
        ProjectMcpTemplateGenerationPreparation preparation = preparationPort.prepare(
                new ProjectMcpTemplateGenerationPreparationRequest(
                        id,
                        resourceId,
                        mcpId,
                        profile,
                        resource,
                        template,
                        command));
        if (preparation == null) {
            throw new IllegalStateException("PROJECT_MCP_TEMPLATE_PREPARATION_REQUIRED");
        }
        LocalDateTime now = LocalDateTime.now();
        ProjectMcpDefinition definition = new ProjectMcpDefinition(
                mcpId,
                text(preparation.mcpName(), mcpId),
                id,
                resourceId,
                text(preparation.resourceType(), actualResourceType),
                text(preparation.transportType(), "stdio"),
                text(preparation.templateId(), templateId),
                preparation.transportConfig(),
                preparation.allowedActions(),
                ProjectMcpRiskLevel.failClosed(text(preparation.riskLevel(), "HIGH")),
                preparation.readOnly(),
                preparation.permissionPolicy(),
                preparation.requestTimeout(),
                ProjectMcpStatus.from(text(preparation.status(), "ENABLED")),
                now,
                now);
        return catalogService.save(definition);
    }

    private Map<String, Object> safe(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String normalizeId(String value) {
        String normalized = text(value, "item").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-");
        normalized = normalized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private String required(Object value, String error) {
        String normalized = text(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
