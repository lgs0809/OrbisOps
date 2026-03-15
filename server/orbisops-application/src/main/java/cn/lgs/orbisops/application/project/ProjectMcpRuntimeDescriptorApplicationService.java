package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;

import java.util.Optional;
import java.util.Set;

public final class ProjectMcpRuntimeDescriptorApplicationService {

    private static final Set<ProjectMcpStatus> DISCOVERY_STATUSES = Set.of(
            ProjectMcpStatus.PENDING_REVIEW,
            ProjectMcpStatus.STALE,
            ProjectMcpStatus.ENABLED);

    private final ProjectMcpCatalogApplicationService catalogService;
    private final ProjectResourceApplicationService resourceService;

    public ProjectMcpRuntimeDescriptorApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectResourceApplicationService resourceService) {
        if (catalogService == null) {
            throw new IllegalArgumentException("PROJECT_MCP_CATALOG_SERVICE_REQUIRED");
        }
        if (resourceService == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_SERVICE_REQUIRED");
        }
        this.catalogService = catalogService;
        this.resourceService = resourceService;
    }

    public Optional<ProjectMcpRuntimeDescriptor> resolveEnabled(
            String projectId,
            String mcpId) {
        return resolve(projectId, mcpId, Set.of(ProjectMcpStatus.ENABLED));
    }

    public Optional<ProjectMcpRuntimeDescriptor> resolveForDiscovery(
            String projectId,
            String mcpId) {
        return resolve(projectId, mcpId, DISCOVERY_STATUSES);
    }

    public Optional<ProjectMcpRuntimeDescriptor> resolveEnabledAny(String mcpId) {
        String id = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        return catalogService.listAll().stream()
                .filter(definition -> id.equals(definition.mcpId()))
                .filter(definition -> ProjectMcpStatus.ENABLED == definition.status())
                .map(definition -> resolveEnabled(
                        definition.projectId(), definition.mcpId()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public boolean existsEnabledAny(String mcpId) {
        String id = text(mcpId);
        return !id.isBlank() && catalogService.listAll().stream()
                .anyMatch(definition -> id.equals(definition.mcpId())
                        && ProjectMcpStatus.ENABLED == definition.status());
    }

    private Optional<ProjectMcpRuntimeDescriptor> resolve(
            String projectId,
            String mcpId,
            Set<ProjectMcpStatus> statuses) {
        String project = required(projectId, "PROJECT_ID_REQUIRED");
        String id = required(mcpId, "PROJECT_MCP_ID_REQUIRED");
        Optional<ProjectMcpDefinition> definition = catalogService.find(project, id)
                .filter(item -> statuses.contains(item.status()));
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        ProjectMcpDefinition mcp = definition.get();
        if ("external_mcp".equals(mcp.resourceType())) {
            return Optional.of(new ProjectMcpRuntimeDescriptor(mcp, null));
        }
        String resourceId = text(mcp.resourceId());
        if (resourceId.isBlank()) {
            return Optional.empty();
        }
        return resourceService.findOptionalResource(project, resourceId)
                .map(resource -> new ProjectMcpRuntimeDescriptor(mcp, resource));
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
