package cn.lgs.orbisops.domain.changepackage.model;

import java.time.LocalDateTime;

public record ChangePackageVersion(long id,
                                   String packageId,
                                   int version,
                                   String packageHash,
                                   String status,
                                   ChangePackageSnapshot snapshot,
                                   String changeSummary,
                                   String createdBy,
                                   LocalDateTime createdAt) {

    public ChangePackageVersion {
        packageId = required(packageId, "CHANGE_PACKAGE_VERSION_PACKAGE_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_NUMBER_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_VERSION_HASH_REQUIRED");
        status = ChangePackageStatus.require(status).name();
        if (snapshot == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_SNAPSHOT_REQUIRED");
        if (!packageHash.equals(snapshot.packageHash())) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_SNAPSHOT_HASH_MISMATCH");
        }
        snapshot = new ChangePackageSnapshot(snapshot.toMap(), packageHash);
        changeSummary = text(changeSummary);
        createdBy = text(createdBy);
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
