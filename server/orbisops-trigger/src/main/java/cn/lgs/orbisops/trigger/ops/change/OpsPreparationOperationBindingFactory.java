package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes request operations and builds their frozen tool bindings. */
final class OpsPreparationOperationBindingFactory {

    OperationBundle create(Object rawSteps, Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        List<Map<String, Object>> operations = normalizeSteps(rawSteps, safeRequest);
        List<Map<String, Object>> bindings = toolBindings(safeRequest, operations);
        return new OperationBundle(operations, bindings);
    }

    private List<Map<String, Object>> normalizeSteps(Object value,
                                                     Map<String, Object> request) {
        List<Object> raw = listValue(value);
        List<Map<String, Object>> steps = new ArrayList<>();
        int index = 1;
        String packageTargetEnvironment = text(firstNonNull(
                request.get("targetEnvironment"),
                request.get("target_environment")), "");
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> source = copy(map);
            Map<String, Object> step = new LinkedHashMap<>(source);
            step.put("operationId", text(firstNonNull(
                    source.get("operationId"), source.get("id")), "op-" + index));
            step.put("toolName", text(firstNonNull(
                    source.get("toolName"),
                    source.get("remoteToolName"),
                    source.get("name")), ""));
            step.put("mcpId", text(source.get("mcpId"), ""));
            step.put(
                    "effectType",
                    ChangePackagePreparationOperation.normalizeEffectType(
                            text(source.get("effectType"), "UNKNOWN")));
            step.put("effectScope", text(source.get("effectScope"), "UNKNOWN"));
            step.put("mutability", text(source.get("mutability"), "UNKNOWN"));
            step.put("riskLevel", text(source.get("riskLevel"), "HIGH"));
            step.put("resourceScope", text(firstNonNull(
                    source.get("resourceScope"),
                    source.get("targetResourceScope")), ""));
            step.put("targetEnvironment", text(firstNonNull(
                    source.get("targetEnvironment"),
                    source.get("target_environment"),
                    source.get("environment"),
                    packageTargetEnvironment), ""));
            step.put(
                    "arguments",
                    source.getOrDefault("arguments", source.getOrDefault("args", Map.of())));
            step.put(
                    "preconditions",
                    source.getOrDefault("preconditions", Map.of(
                            "schemaValid", schemaBound(step),
                            "permissionGranted", false,
                            "targetExists", "UNKNOWN",
                            "resourceVersion", "")));
            step.put("expectedResult", source.getOrDefault("expectedResult", ""));
            step.put("verification", source.getOrDefault("verification", List.of()));
            steps.add(Collections.unmodifiableMap(step));
            index++;
        }
        return List.copyOf(steps);
    }

    private List<Map<String, Object>> toolBindings(
            Map<String, Object> request,
            List<Map<String, Object>> operations) {
        // Tool binding is always derived from the server-authoritative operation projection.
        // Request-supplied toolBindings are untrusted and cannot assert schemaBound=true.
        return operations.stream().map(step -> {
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("operationId", step.get("operationId"));
            binding.put("mcpId", step.get("mcpId"));
            binding.put("toolName", step.get("toolName"));
            binding.put("adapterType", step.getOrDefault("adapterType", ""));
            binding.put("targetEnvironment", step.getOrDefault("targetEnvironment", ""));
            binding.put("schemaHash", step.getOrDefault("schemaHash", ""));
            binding.put("policyId", step.getOrDefault("policyId", ""));
            binding.put("effectType", step.get("effectType"));
            binding.put("effectScope", step.get("effectScope"));
            binding.put("mutability", step.get("mutability"));
            binding.put("riskLevel", step.get("riskLevel"));
            binding.put("readOnly", step.getOrDefault("readOnly", false));
            binding.put("writesTargetResource", step.getOrDefault("writesTargetResource", true));
            binding.put("requiresChangePackage", step.getOrDefault("requiresChangePackage", true));
            binding.put("requiresApproval", step.getOrDefault("requiresApproval", true));
            binding.put("schemaBound", schemaBound(step));
            return Collections.unmodifiableMap(binding);
        }).toList();
    }

    private boolean schemaBound(Map<String, Object> operation) {
        return completeToolIdentity(operation)
                && Boolean.TRUE.equals(operation.get("policyBound"))
                && !text(operation.get("schemaHash"), "").isBlank()
                && !"UNKNOWN".equalsIgnoreCase(text(
                        operation.get("effectType"), "UNKNOWN"))
                && !"UNKNOWN".equalsIgnoreCase(text(
                        operation.get("mutability"), "UNKNOWN"));
    }

    private boolean completeToolIdentity(Map<String, Object> operation) {
        return !text(operation.get("toolName"), "").isBlank()
                && !text(operation.get("mcpId"), "").isBlank();
    }

    private List<Object> listValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> data = new LinkedHashMap<>();
        map.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record OperationBundle(List<Map<String, Object>> operations,
                           List<Map<String, Object>> toolBindings) {
        OperationBundle {
            operations = operations == null ? List.of() : List.copyOf(operations);
            toolBindings = toolBindings == null ? List.of() : List.copyOf(toolBindings);
        }
    }
}
