package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ProjectMcpGenerationApplicationService {

    private final ProjectResourceApplicationService resourceService;
    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectMcpGenerationPreparationPort preparationPort;

    public ProjectMcpGenerationApplicationService(
            ProjectResourceApplicationService resourceService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectMcpGenerationPreparationPort preparationPort) {
        if (resourceService == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_SERVICE_REQUIRED");
        }
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (preparationPort == null) {
            throw new IllegalArgumentException("PROJECT_MCP_PREPARATION_PORT_REQUIRED");
        }
        this.resourceService = resourceService;
        this.catalogService = catalogService;
        this.preparationPort = preparationPort;
    }

    public Map<String, Object> generate(Map<String, Object> request) {
        return catalogService.view(generateDefinition(request));
    }

    public ProjectMcpDefinition generateDefinition(Map<String, Object> request) {
        Map<String, Object> command = request == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request);
        String projectId = required(command.get("projectId"), "PROJECT_ID_REQUIRED");
        String resourceId = required(command.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED");
        String profile = normalizeId(text(command.get("profile"), "readonly"));
        ProjectResourceDefinition resource = resourceService.requireResource(projectId, resourceId);
        String resourceType = resource.type().value();
        String mcpId = normalizeId(projectId + "-" + resourceId + "-"
                + profile + "-mcp");
        ProjectMcpGenerationPreparation preparation = preparationPort.prepare(
                new ProjectMcpGenerationPreparationRequest(
                        projectId, resourceId, mcpId, profile, resource));
        if (preparation == null) {
            throw new IllegalStateException("PROJECT_MCP_PREPARATION_REQUIRED");
        }
        LocalDateTime now = LocalDateTime.now();
        ProjectMcpDefinition definition = new ProjectMcpDefinition(
                mcpId,
                text(preparation.mcpName(), mcpId),
                projectId,
                resourceId,
                text(preparation.resourceType(), resourceType),
                text(preparation.transportType(), "stdio"),
                text(preparation.templateId(), ""),
                preparation.transportConfig(),
                preparation.allowedActions(),
                ProjectMcpRiskLevel.failClosed(text(preparation.riskLevel(), "LOW")),
                preparation.readOnly(),
                preparation.permissionPolicy(),
                preparation.requestTimeout(),
                ProjectMcpStatus.from(text(preparation.status(), "ENABLED")),
                now,
                now);
        return catalogService.save(definition);
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
