package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed authoritative writeback for a completed or reconciled Landing run. */
public record ChangePackageLandingCompletion(ChangePackageStatus status,
                                             String landingRunId,
                                             Map<String, Object> result,
                                             Map<String, Object> failureSummary) {

    public ChangePackageLandingCompletion {
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_STATUS_REQUIRED");
        if (status != ChangePackageStatus.LANDED
                && status != ChangePackageStatus.NEEDS_REPLAN
                && status != ChangePackageStatus.LANDING_FAILED) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_COMPLETION_STATUS_INVALID:" + status.name());
        }
        landingRunId = required(landingRunId, "CHANGE_PACKAGE_LANDING_RUN_ID_REQUIRED");
        result = immutable(result);
        failureSummary = immutable(failureSummary);
        if (status == ChangePackageStatus.LANDED && !failureSummary.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDED_FAILURE_SUMMARY_FORBIDDEN");
        }
        if (status != ChangePackageStatus.LANDED && failureSummary.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_FAILURE_SUMMARY_REQUIRED");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
