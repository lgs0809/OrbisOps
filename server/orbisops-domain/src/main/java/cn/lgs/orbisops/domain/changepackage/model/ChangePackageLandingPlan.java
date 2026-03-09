package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, approved landing plan reconstructed from one sealed ChangePackage version. */
public record ChangePackageLandingPlan(String packageId,
                                       String projectId,
                                       int approvedVersion,
                                       String approvedPackageHash,
                                       Map<String, Object> snapshot,
                                       List<ChangePackageLandingOperation> operations) {

    public ChangePackageLandingPlan {
        packageId = required(packageId, "CHANGE_PACKAGE_LANDING_PACKAGE_ID_REQUIRED");
        projectId = required(projectId, "CHANGE_PACKAGE_LANDING_PROJECT_ID_REQUIRED");
        if (approvedVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_VERSION_INVALID");
        approvedPackageHash = required(approvedPackageHash, "CHANGE_PACKAGE_LANDING_HASH_REQUIRED");
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_SNAPSHOT_REQUIRED");
        }
        snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
