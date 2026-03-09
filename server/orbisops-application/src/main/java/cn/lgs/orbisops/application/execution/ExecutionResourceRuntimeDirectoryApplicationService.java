package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExecutionResourceRuntimeDirectoryApplicationService {

    private final IExecutionResourceRepository repository;
    private final Map<String, ExecutionResource> resources = new ConcurrentHashMap<>();

    public ExecutionResourceRuntimeDirectoryApplicationService(
            IExecutionResourceRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
        List<ExecutionResource> loaded = repository.findAllVisible();
        if (loaded != null) loaded.forEach(this::publish);
    }

    public void publish(ExecutionResource resource) {
        if (resource == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_REQUIRED");
        resources.put(key(resource.projectId(), resource.resourceId()), resource);
    }

    public Optional<ExecutionResource> find(String projectId, String resourceId) {
        String project = text(projectId);
        String resource = text(resourceId);
        if (project.isBlank() || resource.isBlank()) return Optional.empty();
        return Optional.ofNullable(resources.get(key(project, resource)));
    }

    public List<ExecutionResource> list(String projectId) {
        String project = text(projectId);
        return resources.values().stream()
                .filter(resource -> project.isBlank() || project.equals(resource.projectId()))
                .sorted(Comparator.comparing(ExecutionResource::projectId)
                        .thenComparing(ExecutionResource::resourceId))
                .toList();
    }

    public List<ExecutionResource> listByTemplate(String templateId) {
        String normalized = text(templateId);
        if (normalized.isBlank()) return List.of();
        return resources.values().stream()
                .filter(resource -> normalized.equals(resource.adapterTemplateId()))
                .sorted(Comparator.comparing(ExecutionResource::projectId)
                        .thenComparing(ExecutionResource::resourceId))
                .toList();
    }

    public boolean existsWorkerResourceOutsideProject(
            String workerId,
            String resourceId,
            String projectId) {
        String worker = text(workerId);
        String resource = text(resourceId);
        String project = text(projectId);
        boolean inDirectory = resources.values().stream()
                .anyMatch(item -> worker.equals(item.workerId())
                        && resource.equals(item.resourceId())
                        && !project.equals(item.projectId()));
        return inDirectory || repository.existsWorkerResourceOutsideProject(worker, resource, project);
    }

    public int size() {
        return resources.size();
    }

    private String key(String projectId, String resourceId) {
        return projectId + ":" + resourceId;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
