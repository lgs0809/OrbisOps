package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

public record ChangePackageApprovalOutcome(String packageId,
                                           ChangePackageStatus status,
                                           int version,
                                           String packageHash,
                                           int requiredApprovals,
                                           int approvedCount) {

    public ChangePackageApprovalOutcome {
        packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED");
        if (requiredApprovals < 0 || approvedCount < 0) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_COUNT_INVALID");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
