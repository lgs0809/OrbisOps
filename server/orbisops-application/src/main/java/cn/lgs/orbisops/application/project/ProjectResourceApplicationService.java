package cn.lgs.orbisops.application.project;

import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProjectResourceApplicationService {

    private final IProjectResourceRepository repository;
    private final ProjectDefinitionApplicationService projectDefinitionService;
    private final ProjectResourcePreparationPort preparationPort;

    public ProjectResourceApplicationService(
            IProjectResourceRepository repository,
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectResourcePreparationPort preparationPort) {
        if (repository == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_REPOSITORY_REQUIRED");
        }
        if (projectDefinitionService == null) {
            throw new IllegalArgumentException("PROJECT_DEFINITION_SERVICE_REQUIRED");
        }
        if (preparationPort == null) {
            throw new IllegalArgumentException("PROJECT_RESOURCE_PREPARATION_PORT_REQUIRED");
        }
        this.repository = repository;
        this.projectDefinitionService = projectDefinitionService;
        this.preparationPort = preparationPort;
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return view(createResource(request));
    }

    public ProjectResourceDefinition createResource(Map<String, Object> request) {
        Map<String, Object> command = safe(request);
        String projectId = required(command.get("projectId"), "PROJECT_ID_REQUIRED");
        requireProject(projectId);
        ProjectResourceType type = ProjectResourceType.from(text(command.get("type"), "mysql"));
        String environment = text(command.get("environment"), "prod").toLowerCase(Locale.ROOT);
        String name = text(command.get("name"), type.value() + "-" + environment);
        String baseId = normalizeId(text(command.get("resourceId"),
                projectId + "-" + type.value() + "-" + environment));
        String resourceId = uniqueId(projectId, baseId);
        String endpoint = text(command.get("endpoint"), type.defaultEndpoint());
        ProjectResourcePreparation preparation = required(preparationPort.prepare(
                new ProjectResourcePreparationRequest(
                        type.value(),
                        resourceId,
                        endpoint,
                        command,
                        Map.of(),
                        Map.of(),
                        false)));
        LocalDateTime now = LocalDateTime.now();
        ProjectResourceDefinition resource = new ProjectResourceDefinition(
                resourceId,
                projectId,
                type,
                text(preparation.typeName(), type.value()),
                name,
                environment,
                endpoint,
                preparation.credential(),
                text(preparation.status(), "PREVIEW"),
                preparation.schema(),
                preparation.permission(),
                now,
                now);
        return saved(resource);
    }

    public Map<String, Object> update(Map<String, Object> request) {
        return view(updateResource(request));
    }

    public ProjectResourceDefinition updateResource(Map<String, Object> request) {
        Map<String, Object> command = safe(request);
        String projectId = required(command.get("projectId"), "PROJECT_ID_REQUIRED");
        String resourceId = required(command.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED");
        requireProject(projectId);
        ProjectResourceDefinition current = requireResource(projectId, resourceId);
        String name = text(command.get("name"), current.name());
        String environment = text(command.get("environment"), current.environment())
                .toLowerCase(Locale.ROOT);
        String endpoint = text(command.get("endpoint"), current.endpoint());
        ProjectResourcePreparation preparation = required(preparationPort.prepare(
                new ProjectResourcePreparationRequest(
                        current.type().value(),
                        current.resourceId(),
                        endpoint,
                        command,
                        current.credential(),
                        current.permission(),
                        true)));
        ProjectResourceDefinition updated = current.update(
                name,
                environment,
                endpoint,
                preparation.credential(),
                text(preparation.status(), current.status()),
                preparation.schema(),
                preparation.permission(),
                LocalDateTime.now());
        return saved(updated);
    }

    public Map<String, Object> updatePermission(Map<String, Object> request) {
        return view(updatePermissionResource(request));
    }

    public ProjectResourceDefinition updatePermissionResource(Map<String, Object> request) {
        Map<String, Object> command = safe(request);
        String projectId = required(command.get("projectId"), "PROJECT_ID_REQUIRED");
        String resourceId = required(command.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED");
        requireProject(projectId);
        ProjectResourceDefinition current = requireResource(projectId, resourceId);
        Map<String, Object> permission = map(command.get("permission"));
        if (permission.isEmpty()) {
            return current;
        }
        Map<String, Object> enriched = preparationPort.enrichPermission(
                current.type().value(), permission);
        return saved(current.updatePermission(enriched, LocalDateTime.now()));
    }

    public List<Map<String, Object>> list(String projectId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        requireProject(id);
        List<ProjectResourceDefinition> resources = repository.list(id);
        return (resources == null ? List.<ProjectResourceDefinition>of() : resources).stream()
                .map(this::view)
                .toList();
    }

    public Map<String, Object> findView(String projectId, String resourceId) {
        return view(requireResource(
                required(projectId, "PROJECT_ID_REQUIRED"),
                required(resourceId, "PROJECT_RESOURCE_ID_REQUIRED")));
    }

    public Optional<Map<String, Object>> findOptionalView(
            String projectId,
            String resourceId) {
        return findOptionalResource(projectId, resourceId).map(this::view);
    }

    public Optional<ProjectResourceDefinition> findOptionalResource(
            String projectId,
            String resourceId) {
        String id = required(projectId, "PROJECT_ID_REQUIRED");
        String resource = required(resourceId, "PROJECT_RESOURCE_ID_REQUIRED");
        Optional<ProjectResourceDefinition> result = repository.find(id, resource);
        return result == null ? Optional.empty() : result;
    }

    public Map<String, Object> view(ProjectResourceDefinition resource) {
        if (resource == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", resource.resourceId());
        result.put("projectId", resource.projectId());
        result.put("type", resource.type().value());
        result.put("typeName", resource.typeName());
        result.put("name", resource.name());
        result.put("environment", resource.environment());
        result.put("endpoint", resource.endpoint());
        result.put("credential", resource.credential());
        result.put("status", resource.status());
        result.put("schema", resource.schema());
        result.put("permission", resource.permission());
        result.put("createdAt", time(resource.createdAt()));
        result.put("updatedAt", time(resource.updatedAt()));
        return result;
    }

    public ProjectResourceDefinition requireResource(String projectId, String resourceId) {
        return repository.find(projectId, resourceId)
                .orElseThrow(() -> new IllegalArgumentException("资源不存在：" + resourceId));
    }

    private ProjectResourceDefinition saved(ProjectResourceDefinition resource) {
        ProjectResourceDefinition saved = repository.save(resource);
        return saved == null ? resource : saved;
    }

    private ProjectResourcePreparation required(ProjectResourcePreparation preparation) {
        if (preparation == null) {
            throw new IllegalStateException("PROJECT_RESOURCE_PREPARATION_REQUIRED");
        }
        return preparation;
    }

    private void requireProject(String projectId) {
        if (!projectDefinitionService.exists(projectId)) {
            throw new IllegalArgumentException("业务系统不存在：" + projectId);
        }
    }

    private String uniqueId(String projectId, String baseId) {
        String candidate = baseId;
        while (repository.find(projectId, candidate).isPresent()) {
            candidate = baseId + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private String normalizeId(String input) {
        String normalized = text(input, "item").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-");
        normalized = normalized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private Map<String, Object> safe(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String required(Object input, String error) {
        String normalized = text(input, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object input, String fallback) {
        String normalized = input == null ? "" : String.valueOf(input).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
