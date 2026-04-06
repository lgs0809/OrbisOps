package cn.lgs.orbisops.domain.execution.service;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceCapability;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceDraft;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ExecutionResourcePolicy {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");
    private final ExecutionResourceConfigurationPolicy configurationPolicy;

    public ExecutionResourcePolicy() {
        this(new ExecutionResourceConfigurationPolicy());
    }

    ExecutionResourcePolicy(ExecutionResourceConfigurationPolicy configurationPolicy) {
        if (configurationPolicy == null) {
            throw new IllegalArgumentException("EXECUTION_CONFIGURATION_POLICY_REQUIRED");
        }
        this.configurationPolicy = configurationPolicy;
    }

    public ExecutionResourceDraft normalize(
            ExecutionResourceDraft draft,
            List<String> projectEnvironments,
            ExecutionSourceResource sourceResource) {
        if (draft == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_DRAFT_REQUIRED");
        String projectId = id(draft.projectId(), "projectId");
        String resourceId = id(draft.resourceId(), "resourceId");
        String workerId = id(draft.workerId(), "workerId");
        ExecutionAdapterType adapter = ExecutionAdapterType.require(draft.adapter());
        List<String> environments = environments(draft.environments());
        List<String> allowedProjectEnvironments = normalizeProjectEnvironments(projectEnvironments);
        if (!allowedProjectEnvironments.containsAll(environments)) {
            throw new IllegalArgumentException(
                    "执行资源环境必须属于项目环境：" + allowedProjectEnvironments);
        }
        Map<String, Object> configuration = configurationPolicy.normalize(
                adapter, draft.configuration(), environments, sourceResource);
        return new ExecutionResourceDraft(
                resourceId,
                projectId,
                text(draft.name()).isBlank() ? resourceId : text(draft.name()),
                workerId,
                adapter.code(),
                text(draft.adapterTemplateId()),
                environments,
                configuration,
                ExecutionResourceStatus.require(draft.status()).name());
    }

    public ExecutionResource materialize(
            ExecutionResourceDraft draft,
            ExecutionResource existing,
            LocalDateTime now) {
        if (draft == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_DRAFT_REQUIRED");
        if (now == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_TIME_REQUIRED");
        return new ExecutionResource(
                draft.resourceId(),
                draft.projectId(),
                draft.name(),
                draft.workerId(),
                ExecutionAdapterType.require(draft.adapter()),
                draft.adapterTemplateId(),
                draft.environments(),
                draft.configuration(),
                ExecutionResourceStatus.require(draft.status()),
                existing == null ? now : existing.createdAt(),
                now);
    }

    public void assertWorkerResourceUnique(boolean duplicate, String resourceId) {
        if (duplicate) {
            throw new IllegalArgumentException(
                    "同一 Worker 内 resourceId 必须全局唯一：" + text(resourceId));
        }
    }

    public ExecutionResourceStatus status(String value) {
        return ExecutionResourceStatus.require(value);
    }

    public boolean requiresSourceResource(ExecutionAdapterType adapter) {
        return adapter == ExecutionAdapterType.MYSQL_CONTROLLED
                || adapter == ExecutionAdapterType.REDIS_CONTROLLED
                || adapter == ExecutionAdapterType.RABBITMQ_POLICY;
    }

    public String sourceResourceId(ExecutionResourceDraft draft) {
        if (draft == null) return "";
        return text(draft.configuration().get("sourceResourceId"));
    }

    public ExecutionResourceCapability capability(ExecutionResource resource) {
        if (resource == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_REQUIRED");
        Map<String, Object> configuration = resource.configuration();
        List<String> actions = switch (resource.adapter()) {
            case LOCAL_JAVA_SERVICE -> List.of("ARTIFACT_DEPLOY");
            case DEPLOYMENT_HTTP -> List.of("SERVICE_RESTART", "SERVICE_SCALE");
            case MYSQL_CONTROLLED, REDIS_CONTROLLED -> strings(configuration.get("allowedActions"));
            case RABBITMQ_POLICY -> List.of("RABBITMQ_UPSERT_POLICY");
        };
        List<String> targetObjects = switch (resource.adapter()) {
            case LOCAL_JAVA_SERVICE -> new ArrayList<>(map(configuration.get("services")).keySet());
            case MYSQL_CONTROLLED -> strings(configuration.get("allowedObjects"));
            default -> List.of();
        };
        Map<String, Object> constraints = new LinkedHashMap<>();
        switch (resource.adapter()) {
            case MYSQL_CONTROLLED -> {
                constraints.put("allowedVariables", configuration.getOrDefault("allowedVariables", List.of()));
                constraints.put("maxAffectedRows", configuration.getOrDefault("maxAffectedRows", 0));
            }
            case REDIS_CONTROLLED -> {
                constraints.put("allowedPatterns", configuration.getOrDefault("allowedPatterns", List.of()));
                constraints.put("allowedConfigKeys", configuration.getOrDefault("allowedConfigKeys", List.of()));
                constraints.put("maxKeys", configuration.getOrDefault("maxKeys", 0));
                constraints.put("maxSnapshotBytes", configuration.getOrDefault("maxSnapshotBytes", 0));
            }
            case RABBITMQ_POLICY -> {
                constraints.put("allowedVhosts", configuration.getOrDefault("allowedVhosts", List.of()));
                constraints.put("allowedPolicyPrefixes",
                        configuration.getOrDefault("allowedPolicyPrefixes", List.of()));
                constraints.put("allowedDefinitionKeys",
                        configuration.getOrDefault("allowedDefinitionKeys", List.of()));
                constraints.put("maxAffectedObjects",
                        configuration.getOrDefault("maxAffectedObjects", 0));
            }
            default -> {
            }
        }
        return new ExecutionResourceCapability(
                resource.resourceId(), resource.name(), resource.adapter(), resource.environments(),
                actions, targetObjects, constraints);
    }

    public boolean supportsService(ExecutionResource resource, String serviceId) {
        if (resource == null || !resource.enabled()) return false;
        if (resource.adapter() != ExecutionAdapterType.LOCAL_JAVA_SERVICE) return true;
        return map(resource.configuration().get("services")).containsKey(text(serviceId));
    }

    /**
     * Temporary compatibility normalizer for the legacy generic application type.
     * New command paths use the typed overload above and never bypass configuration policy.
     */
    public Map<String, Object> normalize(Map<String, Object> request) {
        Map<String, Object> result = request == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        String projectId = id(result.get("projectId"), "EXECUTION_PROJECT_ID_INVALID");
        String resourceId = id(result.get("resourceId"), "EXECUTION_RESOURCE_ID_INVALID");
        String workerId = id(result.get("workerId"), "EXECUTION_WORKER_ID_INVALID");
        ExecutionAdapterType adapter = ExecutionAdapterType.require(text(result.get("adapter")));
        List<String> environments = environments(valueCollection(result.get("environments")));
        String name = text(result.get("name"));
        result.put("projectId", projectId);
        result.put("resourceId", resourceId);
        result.put("workerId", workerId);
        result.put("name", name.isBlank() ? resourceId : name);
        result.put("adapter", adapter.code());
        result.put("environments", environments);
        result.put("status", ExecutionResourceStatus.require(text(result.get("status"))).name());
        result.put("adapterTemplateId", text(result.get("adapterTemplateId")));
        if (!(result.get("configuration") instanceof Map<?, ?>)) result.put("configuration", Map.of());
        return result;
    }

    private List<String> environments(Collection<String> values) {
        List<String> result = Optional.ofNullable(values).orElse(List.of()).stream()
                .map(this::text)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (result.isEmpty() || result.stream().anyMatch(value -> !ID.matcher(value).matches())) {
            throw new IllegalArgumentException("至少需要一个有效 environment");
        }
        return result;
    }

    private List<String> normalizeProjectEnvironments(Collection<String> values) {
        return Optional.ofNullable(values).orElse(List.of()).stream()
                .map(this::text)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String id(Object value, String field) {
        String normalized = text(value);
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " 格式无效");
        }
        return normalized;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(this::text).filter(item -> !item.isBlank()).toList();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Collection<String> valueCollection(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        return collection.stream().map(String::valueOf).toList();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
