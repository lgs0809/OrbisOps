package cn.lgs.orbisops.application.changepackage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageLandingRun(String runId,
                                      String packageId,
                                      String projectId,
                                      int approvedVersion,
                                      String approvedPackageHash,
                                      String idempotencyKey,
                                      ChangePackageLandingRunStatus status,
                                      boolean leaseExpired,
                                      Map<String, Object> result) {

    public ChangePackageLandingRun {
        runId = required(runId, "CHANGE_PACKAGE_LANDING_RUN_ID_REQUIRED");
        packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        projectId = required(projectId, "CHANGE_PACKAGE_PROJECT_ID_REQUIRED");
        if (approvedVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_VERSION_INVALID");
        approvedPackageHash = required(approvedPackageHash, "CHANGE_PACKAGE_LANDING_HASH_REQUIRED");
        idempotencyKey = required(idempotencyKey, "CHANGE_PACKAGE_LANDING_IDEMPOTENCY_KEY_REQUIRED");
        if (status == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_RUN_STATUS_REQUIRED");
        }
        result = result == null || result.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    public boolean succeeded() {
        return status.succeeded();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
