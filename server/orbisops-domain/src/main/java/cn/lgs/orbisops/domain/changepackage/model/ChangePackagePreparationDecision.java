package cn.lgs.orbisops.domain.changepackage.model;

import java.util.List;

public record ChangePackagePreparationDecision(ChangePackagePreparationAssessment assessment,
                                               ChangePackageType packageType,
                                               ChangePackageStatus status,
                                               String riskLevel,
                                               List<String> limitations,
                                               String reasonCode,
                                               boolean requestedRiskEscalated) {

    public ChangePackagePreparationDecision {
        if (assessment == null) throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_ASSESSMENT_REQUIRED");
        if (packageType == null) throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_TYPE_REQUIRED");
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_STATUS_REQUIRED");
        riskLevel = riskLevel == null ? "MEDIUM" : riskLevel.trim();
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
    }
}
