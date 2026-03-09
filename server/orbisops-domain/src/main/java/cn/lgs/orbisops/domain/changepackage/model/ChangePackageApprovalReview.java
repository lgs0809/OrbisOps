package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;

public record ChangePackageApprovalReview(String riskLevel,
                                          String creator,
                                          String approver,
                                          String actorScope,
                                          boolean adminConfirmation,
                                          boolean validationProofRequired,
                                          String validationAssessment,
                                          boolean rollbackMaterialPresent) {

    public ChangePackageApprovalReview {
        riskLevel = required(riskLevel, "CHANGE_PACKAGE_APPROVAL_RISK_REQUIRED").toUpperCase(Locale.ROOT);
        creator = text(creator);
        approver = required(approver, "CHANGE_PACKAGE_APPROVER_REQUIRED");
        actorScope = text(actorScope).toLowerCase(Locale.ROOT);
        validationAssessment = text(validationAssessment).toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
