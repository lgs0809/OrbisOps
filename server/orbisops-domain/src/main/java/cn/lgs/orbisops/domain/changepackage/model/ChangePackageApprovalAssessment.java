package cn.lgs.orbisops.domain.changepackage.model;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageApprovalPolicy;

/** Domain result used by the approval application orchestration. */
public record ChangePackageApprovalAssessment(ChangePackageSnapshot snapshot,
                                              String riskLevel,
                                              ChangePackageApprovalPolicy.ApprovalRequirement requirement,
                                              String actorScope,
                                              boolean adminConfirmation) {

    public ChangePackageApprovalAssessment {
        if (snapshot == null) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_SNAPSHOT_REQUIRED");
        riskLevel = required(riskLevel, "CHANGE_PACKAGE_APPROVAL_RISK_REQUIRED");
        if (requirement == null) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_REQUIREMENT_REQUIRED");
        actorScope = text(actorScope);
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
