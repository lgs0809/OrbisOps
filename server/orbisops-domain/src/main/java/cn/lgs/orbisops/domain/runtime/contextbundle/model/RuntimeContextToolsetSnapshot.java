package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Published Toolset snapshot consumed by the Runtime Context bounded context. */
public record RuntimeContextToolsetSnapshot(
        String toolsetId,
        String adapterType,
        boolean readOnlyDefault,
        List<RuntimeContextToolPolicySnapshot> tools
) {

    public RuntimeContextToolsetSnapshot {
        toolsetId = required(toolsetId, "RUNTIME_TOOLSET_ID_REQUIRED");
        adapterType = required(adapterType, "RUNTIME_TOOLSET_ADAPTER_REQUIRED");
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public Map<String, Object> canonicalView() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolsetId", toolsetId);
        result.put("adapterType", adapterType);
        result.put("readOnlyDefault", readOnlyDefault);
        result.put("tools", tools.stream()
                .map(RuntimeContextToolPolicySnapshot::canonicalView)
                .toList());
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
