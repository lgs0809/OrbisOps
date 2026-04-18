package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable Application result returned by the fixed Landing runtime boundary.
 * Open executor details remain in the payload, while aggregate-driving fields
 * are explicit and cannot silently drift from their serialized projection.
 */
public record ChangePackageLandingRuntimeResult(
        ChangePackageStatus status,
        String eventType,
        String reasonCode,
        String summary,
        boolean executedProductionAction,
        Map<String, Object> payload) {

    public ChangePackageLandingRuntimeResult {
        if (status == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_RUNTIME_STATUS_REQUIRED");
        }
        eventType = normalized(eventType, defaultEventType(status));
        reasonCode = normalized(reasonCode, "");
        summary = normalized(summary, "LandingRuntime 已完成一次状态评估");

        Map<String, Object> projected = new LinkedHashMap<>();
        if (payload != null) projected.putAll(payload);
        projected.put("status", status.name());
        projected.put("eventType", eventType);
        if (reasonCode.isBlank()) projected.remove("reasonCode");
        else projected.put("reasonCode", reasonCode);
        projected.put("summary", summary);
        projected.put("executedProductionAction", executedProductionAction);
        payload = Collections.unmodifiableMap(projected);
    }

    public ChangePackageLandingRunStatus runStatus() {
        return switch (status) {
            case LANDED -> ChangePackageLandingRunStatus.SUCCEEDED;
            case NEEDS_REPLAN -> ChangePackageLandingRunStatus.NEEDS_REPLAN;
            case LANDING_FAILED -> ChangePackageLandingRunStatus.FAILED;
            default -> ChangePackageLandingRunStatus.FAILED;
        };
    }

    public Map<String, Object> mutablePayload() {
        return new LinkedHashMap<>(payload);
    }

    private static String defaultEventType(ChangePackageStatus status) {
        return switch (status) {
            case LANDED -> "LANDING_SUCCEEDED";
            case LANDING_FAILED -> "LANDING_FAILED";
            case NEEDS_REPLAN -> "LANDING_NEEDS_REPLAN";
            default -> "LANDING_EVENT";
        };
    }

    private static String normalized(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
