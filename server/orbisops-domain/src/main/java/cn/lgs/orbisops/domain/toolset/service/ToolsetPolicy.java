package cn.lgs.orbisops.domain.toolset.service;

import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ToolsetPolicy {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> DANGEROUS_COMMAND_TERMS = Set.of(
            "kubectl apply", "kubectl patch", "kubectl delete", "helm upgrade",
            "mysql update", "mysql delete", "mysql alter", "mysql drop",
            "redis del", "redis flushall", "redis flushdb", "redis config set",
            "nacos publish", "jenkins deploy", "ssh", "scp", "sudo", "rm ",
            "chmod", "chown", "curl | sh", "wget | sh");

    public Map<String, Object> customToolset(String projectId,
                                            Map<String, Object> request,
                                            String generatedId) {
        String project = requiredId(projectId, "TOOLSET_PROJECT_ID_INVALID");
        Map<String, Object> safe = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        String toolsetId = text(safe.get("toolsetId"));
        if (toolsetId.isBlank()) toolsetId = requiredId(generatedId, "TOOLSET_ID_INVALID");
        else toolsetId = requiredId(toolsetId, "TOOLSET_ID_INVALID");
        String adapterType = text(safe.get("adapterType")).toUpperCase(Locale.ROOT);
        if (adapterType.isBlank()) adapterType = "MCP";
        List<Map<String, Object>> tools = normalizeTools(safe.get("tools"), adapterType);
        boolean allReadOnly = tools.stream().allMatch(tool -> Boolean.TRUE.equals(tool.get("readOnly")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", project);
        result.put("toolsetId", toolsetId);
        result.put("name", fallback(safe.get("name"), toolsetId));
        result.put("description", text(safe.get("description")));
        result.put("prerequisites", fallback(safe.get("prerequisites"), project));
        result.put("tags", strings(safe.get("tags")));
        result.put("sourceType", "CUSTOM");
        result.put("adapterType", adapterType);
        result.put("enabled", bool(safe.get("enabled"), true));
        result.put("readOnlyDefault", allReadOnly && bool(safe.get("readOnlyDefault"), true));
        result.put("tools", tools);
        return result;
    }

    public Map<String, Object> execution(Map<String, Object> request, String actor) {
        Map<String, Object> result = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        result.put("projectId", requiredId(result.get("projectId"), "TOOL_EXECUTION_PROJECT_ID_INVALID"));
        result.put("toolsetId", requiredId(result.get("toolsetId"), "TOOL_EXECUTION_TOOLSET_ID_INVALID"));
        result.put("toolName", requiredId(result.get("toolName"), "TOOL_EXECUTION_TOOL_NAME_INVALID"));
        result.put("userId", fallback(result.get("userId"), required(actor, "TOOL_EXECUTION_ACTOR_REQUIRED")));
        if (!(result.get("arguments") instanceof Map<?, ?>) && !(result.get("input") instanceof Map<?, ?>)) {
            result.put("arguments", Map.of());
        }
        return result;
    }

    public String projectId(String value) { return requiredId(value, "TOOLSET_PROJECT_ID_INVALID"); }
    public String toolsetId(String value) { return requiredId(value, "TOOLSET_ID_INVALID"); }

    private List<Map<String, Object>> normalizeTools(Object value, String defaultAdapter) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> source)) continue;
            Map<String, Object> tool = new LinkedHashMap<>();
            source.forEach((key, entry) -> tool.put(String.valueOf(key), entry));
            result.add(normalizeTool(tool, defaultAdapter));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> normalizeTool(Map<String, Object> source, String defaultAdapter) {
        String toolName = requiredId(first(source.get("toolName"), source.get("name")), "TOOL_NAME_INVALID");
        String command = text(source.get("commandTemplate"));
        boolean targetWrite = bool(source.get("writesTargetResource"), false) || dangerous(command);
        boolean repairWrite = bool(source.get("writesRepairWorkspace"), false);
        if (targetWrite && repairWrite) {
            throw new IllegalArgumentException("TOOL_WRITE_SCOPE_AMBIGUOUS:" + toolName);
        }
        boolean readOnly = !targetWrite && !repairWrite && bool(source.get("readOnly"), true);
        ToolRiskLevel fallbackRisk = targetWrite ? ToolRiskLevel.HIGH
                : repairWrite ? ToolRiskLevel.MEDIUM : ToolRiskLevel.LOW;
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("toolName", toolName);
        result.put("displayName", fallback(first(source.get("displayName"), source.get("name")), toolName));
        result.put("description", text(source.get("description")));
        result.put("adapterType", fallback(source.get("adapterType"), defaultAdapter).toUpperCase(Locale.ROOT));
        result.put("commandTemplate", command);
        result.put("readOnly", readOnly);
        result.put("writesRepairWorkspace", repairWrite);
        result.put("writesTargetResource", targetWrite);
        result.put("requiresChangePackage", targetWrite || bool(source.get("requiresChangePackage"), false));
        result.put("requiresApproval", targetWrite || bool(source.get("requiresApproval"), false));
        result.put("riskLevel", ToolRiskLevel.require(text(source.get("riskLevel")), fallbackRisk).name());
        result.put("enabled", bool(source.get("enabled"), true));
        return result;
    }

    private boolean dangerous(String command) {
        String lower = text(command).toLowerCase(Locale.ROOT);
        return DANGEROUS_COMMAND_TERMS.stream().anyMatch(lower::contains);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String normalized = text(item);
            if (!normalized.isBlank() && !result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String requiredId(Object value, String error) {
        String normalized = text(value);
        if (!ID.matcher(normalized).matches()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return normalized.isBlank() ? fallback : Boolean.parseBoolean(normalized) || "1".equals(normalized);
    }

    private Object first(Object left, Object right) {
        return left == null || text(left).isBlank() ? right : left;
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
