package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable tool-policy facts frozen into one runtime context boundary. */
public record RuntimeContextToolPolicySnapshot(
        String toolName,
        String adapterType,
        boolean readOnly,
        boolean writesRepairWorkspace,
        boolean writesTargetResource,
        boolean requiresChangePackage,
        boolean requiresApproval,
        RuntimeContextToolRisk riskLevel,
        String parametersJson
) {

    public RuntimeContextToolPolicySnapshot {
        toolName = required(toolName, "RUNTIME_TOOL_NAME_REQUIRED");
        adapterType = required(adapterType, "RUNTIME_TOOL_ADAPTER_REQUIRED");
        if (riskLevel == null) throw new IllegalArgumentException("RUNTIME_TOOL_RISK_REQUIRED");
        parametersJson = parametersJson == null ? "" : parametersJson.trim();
    }

    public Map<String, Object> canonicalView() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolName", toolName);
        result.put("adapterType", adapterType);
        result.put("readOnly", readOnly);
        result.put("writesRepairWorkspace", writesRepairWorkspace);
        result.put("writesTargetResource", writesTargetResource);
        result.put("requiresChangePackage", requiresChangePackage);
        result.put("requiresApproval", requiresApproval);
        result.put("riskLevel", riskLevel.name());
        result.put("parametersJson", parametersJson);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
