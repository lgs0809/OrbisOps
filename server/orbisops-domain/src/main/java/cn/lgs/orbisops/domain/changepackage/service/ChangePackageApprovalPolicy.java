package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalReview;

import java.util.List;

public final class ChangePackageApprovalPolicy {

    private static final List<String> RISK_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public ApprovalRequirement evaluate(ChangePackageApprovalReview review) {
        if (review == null) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_REVIEW_REQUIRED");
        int riskRank = riskRank(review.riskLevel());
        if (!review.creator().isBlank() && review.creator().equals(review.approver())) {
            throw new SecurityException("CHANGE_PACKAGE_SELF_APPROVAL_FORBIDDEN");
        }
        if (riskRank >= riskRank("MEDIUM") && review.validationProofRequired()
                && !"ACCEPTABLE".equals(review.validationAssessment())) {
            throw new IllegalStateException("CHANGE_PACKAGE_VALIDATION_REQUIRED_BEFORE_APPROVAL");
        }
        if ("CRITICAL".equals(review.riskLevel())) {
            if (!review.adminConfirmation() || !"admin".equals(review.actorScope())) {
                throw new SecurityException("CHANGE_PACKAGE_CRITICAL_ADMIN_CONFIRMATION_REQUIRED");
            }
            if (!review.rollbackMaterialPresent()) {
                throw new IllegalStateException("CHANGE_PACKAGE_CRITICAL_ROLLBACK_MATERIAL_REQUIRED");
            }
        }
        return new ApprovalRequirement(riskRank >= riskRank("HIGH") ? 2 : 1,
                riskRank >= riskRank("MEDIUM"), "CRITICAL".equals(review.riskLevel()));
    }

    private int riskRank(String riskLevel) {
        int index = RISK_ORDER.indexOf(riskLevel == null ? "" : riskLevel.toUpperCase(java.util.Locale.ROOT));
        if (index < 0) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_RISK_UNKNOWN:" + riskLevel);
        return index;
    }

    public record ApprovalRequirement(int requiredApprovals,
                                      boolean validationRequired,
                                      boolean adminConfirmationRequired) {
    }
}
