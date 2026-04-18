package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed orchestration outcome for pre-approval validation plus API detail projection. */
public record ChangePackagePreApprovalValidationOutcome(
        ChangePackageValidationOutcome writebackOutcome,
        ChangePackageValidationReport report,
        Map<String, Object> packageView
) {

    public ChangePackagePreApprovalValidationOutcome {
        if (writebackOutcome == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_OUTCOME_REQUIRED");
        }
        if (report == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VALIDATION_REPORT_REQUIRED");
        }
        packageView = packageView == null || packageView.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(packageView));
    }

    public String packageId() {
        return writebackOutcome.packageId();
    }

    public int version() {
        return writebackOutcome.version();
    }

    public boolean passed() {
        return writebackOutcome.passed();
    }

    public String reasonCode() {
        return writebackOutcome.reasonCode();
    }
}
