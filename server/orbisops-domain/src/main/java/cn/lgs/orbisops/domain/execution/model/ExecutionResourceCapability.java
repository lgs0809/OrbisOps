package cn.lgs.orbisops.domain.execution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionResourceCapability(
        String resourceId,
        String name,
        ExecutionAdapterType adapter,
        List<String> environments,
        List<String> allowedActions,
        List<String> targetObjects,
        Map<String, Object> constraints) {

    public ExecutionResourceCapability {
        resourceId = text(resourceId);
        name = text(name);
        if (adapter == null) throw new IllegalArgumentException("EXECUTION_ADAPTER_REQUIRED");
        environments = environments == null ? List.of() : List.copyOf(environments);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        targetObjects = targetObjects == null ? List.of() : List.copyOf(targetObjects);
        constraints = constraints == null || constraints.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
