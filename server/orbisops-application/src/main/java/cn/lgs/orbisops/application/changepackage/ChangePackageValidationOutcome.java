package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

/** Authoritative validation writeback outcome for one ChangePackage version. */
public record ChangePackageValidationOutcome(
        String packageId,
        ChangePackageStatus status,
        int version,
        String packageHash,
        boolean passed,
        String reasonCode
) {

    public ChangePackageValidationOutcome {
        packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_STATUS_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED");
        reasonCode = required(reasonCode, "CHANGE_PACKAGE_VALIDATION_REASON_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
