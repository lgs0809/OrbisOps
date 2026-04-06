package cn.lgs.orbisops.domain.execution.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionResourceDraft(
        String resourceId,
        String projectId,
        String name,
        String workerId,
        String adapter,
        String adapterTemplateId,
        List<String> environments,
        Map<String, Object> configuration,
        String status) {

    public ExecutionResourceDraft {
        environments = environments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(environments));
        configuration = immutableMap(configuration);
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
}
