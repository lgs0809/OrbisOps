package cn.lgs.orbisops.domain.changepackage.model;

/** Aggregate approval safety facts for a frozen executable operation set. */
public record ChangePackageApprovalOperationAssessment(String effectiveRiskLevel,
                                                       boolean trustedProofRequired) {

    public ChangePackageApprovalOperationAssessment {
        effectiveRiskLevel = effectiveRiskLevel == null
                ? "MEDIUM"
                : effectiveRiskLevel.trim().toUpperCase(java.util.Locale.ROOT);
        if (effectiveRiskLevel.isBlank()) effectiveRiskLevel = "MEDIUM";
    }
}
