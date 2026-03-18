package cn.lgs.orbisops.domain.evidence.model;

public record TrustedProofCriteria(
        String projectId,
        String packageId,
        int packageVersion,
        String packageHash,
        String riskLevel,
        String proofType,
        String externalRunIdOrProofId) {

    public TrustedProofCriteria {
        projectId = required(projectId, "TRUSTED_PROOF_PROJECT_ID_REQUIRED");
        packageId = required(packageId, "TRUSTED_PROOF_PACKAGE_ID_REQUIRED");
        if (packageVersion <= 0) throw new IllegalArgumentException("TRUSTED_PROOF_PACKAGE_VERSION_INVALID");
        packageHash = required(packageHash, "TRUSTED_PROOF_PACKAGE_HASH_REQUIRED");
        riskLevel = required(riskLevel, "TRUSTED_PROOF_RISK_LEVEL_REQUIRED").toUpperCase();
        proofType = required(proofType, "TRUSTED_PROOF_TYPE_REQUIRED").toUpperCase();
        externalRunIdOrProofId = value(externalRunIdOrProofId);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
    private static String value(String value) { return value == null ? "" : value.trim(); }
}
