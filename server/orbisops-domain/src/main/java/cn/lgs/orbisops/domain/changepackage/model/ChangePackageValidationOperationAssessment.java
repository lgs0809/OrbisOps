package cn.lgs.orbisops.domain.changepackage.model;

import java.util.List;

/** Aggregate validation facts for a frozen operation set. */
public record ChangePackageValidationOperationAssessment(List<String> errors,
                                                         String effectiveRiskLevel,
                                                         boolean trustedProofRequired) {

    public ChangePackageValidationOperationAssessment {
        errors = errors == null ? List.of() : List.copyOf(errors);
        effectiveRiskLevel = effectiveRiskLevel == null
                ? "MEDIUM"
                : effectiveRiskLevel.trim().toUpperCase();
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
