package cn.lgs.orbisops.domain.worksession.runtime.model;

import java.time.Instant;

/** Approval-frozen ChangePackage identity used to authorize one LANDING run. */
public record ApprovedPackageSnapshot(
        String packageId,
        long packageVersion,
        String packageHash,
        String projectId,
        String targetEnvironment,
        String artifactDigest,
        Instant approvalExpiresAt) {

    public static final long DEFAULT_APPROVAL_AUTHORITY_TTL_SECONDS = 1800L;

    public ApprovedPackageSnapshot {
        packageId = required(packageId, "PACKAGE_ID_REQUIRED");
        if (packageVersion <= 0L) {
            throw new IllegalArgumentException("APPROVED_PACKAGE_VERSION_INVALID");
        }
        packageHash = required(packageHash, "PACKAGE_HASH_REQUIRED");
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        targetEnvironment = required(targetEnvironment, "TARGET_ENVIRONMENT_REQUIRED");
        artifactDigest = text(artifactDigest);
        if (approvalExpiresAt == null || !approvalExpiresAt.isAfter(Instant.EPOCH)) {
            throw new IllegalArgumentException("APPROVED_PACKAGE_EXPIRY_REQUIRED");
        }
    }

    public boolean expired(Instant now) {
        Instant instant = now == null ? Instant.now() : now;
        return !approvalExpiresAt.isAfter(instant);
    }

    private static String required(Object value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
