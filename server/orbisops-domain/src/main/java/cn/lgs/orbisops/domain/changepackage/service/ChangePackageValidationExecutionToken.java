package cn.lgs.orbisops.domain.changepackage.service;

import java.util.Map;

/** Deterministic capability token binding validation writeback to one immutable current version. */
public final class ChangePackageValidationExecutionToken {

    public String issue(String packageId, int version, String packageHash) {
        String normalizedPackageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
        String normalizedHash = required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED");
        return ChangePackageCanonicalHasher.canonicalHash(Map.of(
                "purpose", "PRE_APPROVAL_VALIDATION_WRITEBACK",
                "packageId", normalizedPackageId,
                "version", version,
                "packageHash", normalizedHash));
    }

    public void verify(String token, String packageId, int version, String packageHash) {
        if (!issue(packageId, version, packageHash).equals(required(token,
                "CHANGE_PACKAGE_VALIDATION_EXECUTION_TOKEN_REQUIRED"))) {
            throw new SecurityException("VALIDATION_RESULT_UNTRUSTED：验证通过只能由真实验证执行结果写回");
        }
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
