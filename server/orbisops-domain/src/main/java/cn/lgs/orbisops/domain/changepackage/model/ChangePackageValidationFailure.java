package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed authoritative writeback for a failed pre-approval validation. */
public record ChangePackageValidationFailure(String assessment,
                                             String reasonCode,
                                             Map<String, Object> failureSummary) {

    public ChangePackageValidationFailure {
        assessment = required(assessment, "CHANGE_PACKAGE_VALIDATION_ASSESSMENT_REQUIRED");
        reasonCode = required(reasonCode, "CHANGE_PACKAGE_VALIDATION_REASON_CODE_REQUIRED");
        if (failureSummary == null || failureSummary.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_FAILURE_SUMMARY_REQUIRED");
        }
        failureSummary = Collections.unmodifiableMap(new LinkedHashMap<>(failureSummary));
    }

    public ChangePackageStatus status() {
        return ChangePackageStatus.VALIDATION_FAILED;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
