package cn.lgs.orbisops.domain.execution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionTargetSpecification(
        String projectId,
        String targetId,
        String targetName,
        String workerId,
        ExecutionAdapterType adapterType,
        String templateId,
        List<String> environments,
        Map<String, Object> configuration,
        ExecutionResourceStatus status
) {

    public ExecutionTargetSpecification {
        projectId = value(projectId);
        targetId = value(targetId);
        targetName = value(targetName);
        workerId = value(workerId);
        if (adapterType == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_ADAPTER_REQUIRED");
        }
        templateId = value(templateId);
        environments = environments == null ? List.of() : List.copyOf(environments);
        configuration = configuration == null || configuration.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
        status = status == null ? ExecutionResourceStatus.ENABLED : status;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
