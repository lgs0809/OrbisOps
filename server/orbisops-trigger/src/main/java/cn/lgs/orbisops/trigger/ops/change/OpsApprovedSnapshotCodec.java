package cn.lgs.orbisops.trigger.ops.change;

import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single parser for the approved snapshot consumed by locks, journals and runtime. */
@Component
public class OpsApprovedSnapshotCodec {

    public ApprovedLandingPlan decode(Map<String, Object> current, Map<String, Object> approvedVersion) {
        Map<String, Object> snapshot = snapshot(approvedVersion);
        if (snapshot.isEmpty()) {
            throw new IllegalStateException("APPROVED_SNAPSHOT_MISSING：已审批版本缺少 snapshot_json");
        }
        String packageId = text(firstNonNull(snapshot.get("packageId"), current.get("package_id"), current.get("packageId")));
        String projectId = text(firstNonNull(snapshot.get("projectId"), current.get("project_id"), current.get("projectId")));
        int version = intValue(firstNonNull(approvedVersion.get("version"), current.get("approved_version"),
                current.get("approvedVersion"), snapshot.get("version")));
        String packageHash = text(firstNonNull(approvedVersion.get("package_hash"), approvedVersion.get("packageHash"),
                current.get("approved_package_hash"), current.get("approvedPackageHash"), snapshot.get("packageHash")));
        if (!StringUtils.hasText(packageId) || !StringUtils.hasText(projectId) || version <= 0 || !StringUtils.hasText(packageHash)) {
            throw new IllegalStateException("APPROVED_SNAPSHOT_IDENTITY_INCOMPLETE：package/project/version/hash 必须完整");
        }
        List<ApprovedOperation> operations = new ArrayList<>();
        List<Map<String, Object>> rawOperations = operations(snapshot);
        for (int index = 0; index < rawOperations.size(); index++) {
            Map<String, Object> raw = new LinkedHashMap<>(rawOperations.get(index));
            String operationId = text(firstNonNull(raw.get("operationId"), raw.get("operation_id")));
            operations.add(new ApprovedOperation(
                    operationId,
                    text(firstNonNull(raw.get("operationHash"), raw.get("operation_hash"))),
                    text(firstNonNull(raw.get("adapterType"), raw.get("adapter_type"),
                            StringUtils.hasText(text(raw.get("mcpId"))) ? "MCP" : "")),
                    text(firstNonNull(raw.get("toolsetId"), raw.get("toolset_id"), raw.get("mcpId"))),
                    text(firstNonNull(raw.get("toolName"), raw.get("tool_name"), raw.get("remoteToolName"))),
                    text(firstNonNull(raw.get("resourceKey"), raw.get("resource_key"), raw.get("resourceScope"))),
                    text(firstNonNull(raw.get("effectType"), raw.get("effect_type"))),
                    Collections.unmodifiableMap(raw)));
        }
        return new ApprovedLandingPlan(packageId, projectId, version, packageHash,
                Collections.unmodifiableMap(snapshot), List.copyOf(operations));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> snapshot(Map<String, Object> approvedVersion) {
        Object value = firstNonNull(approvedVersion.get("snapshot"), approvedVersion.get("snapshot_json"), approvedVersion.get("snapshotJson"));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            return new LinkedHashMap<>(JSON.parseObject(String.valueOf(value)));
        }
        return Map.of();
    }

    private List<Map<String, Object>> operations(Map<String, Object> snapshot) {
        Map<String, Object> landingPlan = map(firstNonNull(snapshot.get("landingPlan"), snapshot.get("landingPlanJson")));
        Map<String, Object> preferred = map(landingPlan.get("preferredPlan"));
        List<Map<String, Object>> operations = list(firstNonNull(preferred.get("steps"), preferred.get("operations"), preferred.get("mcpSteps")));
        if (!operations.isEmpty()) return operations;
        operations = list(firstNonNull(landingPlan.get("steps"), landingPlan.get("operations"), landingPlan.get("mcpSteps")));
        if (!operations.isEmpty()) return operations;
        return list(firstNonNull(snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson")));
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return new LinkedHashMap<>(JSON.parseObject(String.valueOf(value)));
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private List<Map<String, Object>> list(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> mapped = map(item);
                if (!mapped.isEmpty()) result.add(mapped);
            }
            return result;
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return JSON.parseArray(String.valueOf(value), Map.class).stream()
                        .<Map<String, Object>>map(item -> new LinkedHashMap<>((Map<String, Object>) item)).toList();
            } catch (RuntimeException ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record ApprovedLandingPlan(String packageId,
                                      String projectId,
                                      int approvedVersion,
                                      String approvedPackageHash,
                                      Map<String, Object> snapshot,
                                      List<ApprovedOperation> operations) {
    }

    public record ApprovedOperation(String operationId,
                                    String operationHash,
                                    String adapterType,
                                    String toolsetId,
                                    String toolName,
                                    String resourceKey,
                                    String effectType,
                                    Map<String, Object> raw) {
    }
}
