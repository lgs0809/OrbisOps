package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable public projection of the model's operational boundary. */
final class OpsToolOperationBoundaryProjector {

    Map<String, Object> project(ToolExposureSettings settings) {
        ToolExposureSettings effective = settings == null
                ? ToolExposureSettings.defaults()
                : settings;
        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("mode", effective.actionMode());
        boundary.put("analysisOnly", effective.analysisOnly());
        boundary.put("aiCanExecuteRecovery", false);
        boundary.put("aiCanCreateChangePackage", true);
        boundary.put("aiAllowed", List.of(
                "analysis",
                "evidence_collection",
                "recommendation",
                "change_package_prepare",
                "notification"));
        boundary.put("aiForbidden", List.of(
                "restart",
                "scale",
                "rollback",
                "release",
                "feature_switch",
                "sql_write",
                "traffic_control",
                "cache_clear",
                "self_healing"));
        boundary.put("toolCapabilityPolicy", Map.of(
                "policyMode", "EXPLICIT_ALLOWLIST",
                "allowed", List.of(
                        "read_only", "readonly", "read", "query", "search", "list",
                        "get", "evidence", "observe", "inspect",
                        "notification", "notify", "notice", "message", "push_report"),
                "blocked", List.of(
                        "mutating", "write", "execute", "dangerous", "recovery",
                        "admin", "blocked"),
                "fallback", "deny when capability is missing or unknown"));
        boundary.put("recommendationRequiresHuman", true);
        return boundary;
    }
}
