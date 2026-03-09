package cn.lgs.orbisops.domain.execution.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionResource(
        String resourceId,
        String projectId,
        String name,
        String workerId,
        ExecutionAdapterType adapter,
        String adapterTemplateId,
        List<String> environments,
        Map<String, Object> configuration,
        ExecutionResourceStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public ExecutionResource {
        resourceId = required(resourceId, "EXECUTION_RESOURCE_ID_REQUIRED");
        projectId = required(projectId, "EXECUTION_PROJECT_ID_REQUIRED");
        name = required(name, "EXECUTION_RESOURCE_NAME_REQUIRED");
        workerId = required(workerId, "EXECUTION_WORKER_ID_REQUIRED");
        if (adapter == null) throw new IllegalArgumentException("EXECUTION_ADAPTER_REQUIRED");
        adapterTemplateId = text(adapterTemplateId);
        environments = environments == null ? List.of() : List.copyOf(environments);
        configuration = immutableMap(configuration);
        if (status == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_STATUS_REQUIRED");
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("EXECUTION_RESOURCE_TIME_REQUIRED");
        }
    }

    public boolean enabled() {
        return status == ExecutionResourceStatus.ENABLED;
    }

    public ExecutionResource withStatus(ExecutionResourceStatus target, LocalDateTime changedAt) {
        return new ExecutionResource(resourceId, projectId, name, workerId, adapter,
                adapterTemplateId, environments, configuration, target, createdAt, changedAt);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), copyValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), copyValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(item -> copy.add(copyValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
