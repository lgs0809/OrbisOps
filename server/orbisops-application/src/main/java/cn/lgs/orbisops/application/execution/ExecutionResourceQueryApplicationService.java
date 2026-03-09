package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceCapability;
import cn.lgs.orbisops.domain.execution.service.ExecutionResourcePolicy;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ExecutionResourceQueryApplicationService {

    private final ExecutionResourceRuntimeDirectoryApplicationService directory;
    private final ExecutionResourcePolicy policy;
    private final boolean enabled;

    public ExecutionResourceQueryApplicationService(
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            boolean enabled) {
        this(directory, new ExecutionResourcePolicy(), enabled);
    }

    ExecutionResourceQueryApplicationService(
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            ExecutionResourcePolicy policy,
            boolean enabled) {
        if (directory == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_DIRECTORY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_POLICY_REQUIRED");
        this.directory = directory;
        this.policy = policy;
        this.enabled = enabled;
    }

    public ExecutionResourceCapabilities capabilities() {
        return new ExecutionResourceCapabilities(
                enabled,
                java.util.Arrays.stream(ExecutionAdapterType.values())
                        .map(ExecutionAdapterType::code)
                        .toList(),
                directory.size(),
                false,
                true);
    }

    public List<ExecutionResource> list(String projectId) {
        assertEnabled();
        return directory.list(projectId);
    }

    public Optional<ExecutionResource> find(String projectId, String resourceId) {
        if (!enabled) return Optional.empty();
        return directory.find(projectId, resourceId);
    }

    public ExecutionResource get(String projectId, String resourceId) {
        String resource = text(resourceId);
        return find(projectId, resource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EXECUTION_RESOURCE_NOT_FOUND:" + resource));
    }

    public List<ExecutionResourceCapability> proposalCatalog(String projectId) {
        return proposalCatalog(projectId, null);
    }

    public List<ExecutionResourceCapability> proposalCatalog(
            String projectId,
            Collection<String> allowedResourceIds) {
        if (!enabled || text(projectId).isBlank()) return List.of();
        Set<String> allowed = Optional.ofNullable(allowedResourceIds).orElse(List.of()).stream()
                .map(this::text)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
        if (allowedResourceIds != null && allowed.isEmpty()) return List.of();
        return directory.list(projectId).stream()
                .filter(ExecutionResource::enabled)
                .filter(resource -> allowedResourceIds == null || allowed.contains(resource.resourceId()))
                .sorted(Comparator.comparing(ExecutionResource::resourceId))
                .map(policy::capability)
                .toList();
    }

    public List<ExecutionResource> listByTemplate(String templateId) {
        if (!enabled) return List.of();
        return directory.listByTemplate(templateId);
    }

    public boolean supportsService(String projectId, String resourceId, String serviceId) {
        return find(projectId, resourceId)
                .map(resource -> policy.supportsService(resource, serviceId))
                .orElse(false);
    }

    public Map<String, ExecutionResourceRuntimeBinding> workerResources(String workerId) {
        assertEnabled();
        String worker = required(workerId, "workerId");
        Map<String, ExecutionResourceRuntimeBinding> result = new LinkedHashMap<>();
        directory.list("").stream()
                .filter(resource -> worker.equals(resource.workerId()))
                .filter(ExecutionResource::enabled)
                .sorted(Comparator.comparing(ExecutionResource::resourceId))
                .forEach(resource -> result.put(resource.resourceId(),
                        new ExecutionResourceRuntimeBinding(
                                resource.projectId(), resource.adapter(), resource.environments(),
                                resource.configuration())));
        return result;
    }

    private void assertEnabled() {
        if (!enabled) throw new IllegalStateException("项目执行资源能力未启用");
    }

    private String required(String value, String field) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 格式无效");
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
