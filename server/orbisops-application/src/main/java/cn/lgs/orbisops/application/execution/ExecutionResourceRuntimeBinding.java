package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExecutionResourceRuntimeBinding(
        String projectId,
        ExecutionAdapterType adapter,
        List<String> environments,
        Map<String, Object> configuration) {

    public ExecutionResourceRuntimeBinding {
        projectId = projectId == null ? "" : projectId.trim();
        if (adapter == null) throw new IllegalArgumentException("EXECUTION_ADAPTER_REQUIRED");
        environments = environments == null ? List.of() : List.copyOf(environments);
        configuration = configuration == null || configuration.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
    }
}
