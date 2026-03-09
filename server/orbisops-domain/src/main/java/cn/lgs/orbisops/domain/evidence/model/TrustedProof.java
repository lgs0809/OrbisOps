package cn.lgs.orbisops.domain.evidence.model;

import java.util.Map;

public record TrustedProof(
        String proofId,
        String projectId,
        String packageId,
        int packageVersion,
        String packageHash,
        String proofType,
        TrustedProofSource source,
        String externalRunId,
        String commandHash,
        String scriptHash,
        TrustedProofStatus resultStatus,
        String riskLevel,
        Map<String, Object> metadata,
        String createdBy,
        String createdAt) {

    public TrustedProof {
        proofId = required(proofId, "TRUSTED_PROOF_ID_REQUIRED");
        projectId = required(projectId, "TRUSTED_PROOF_PROJECT_ID_REQUIRED");
        packageId = required(packageId, "TRUSTED_PROOF_PACKAGE_ID_REQUIRED");
        if (packageVersion <= 0) throw new IllegalArgumentException("TRUSTED_PROOF_PACKAGE_VERSION_INVALID");
        packageHash = required(packageHash, "TRUSTED_PROOF_PACKAGE_HASH_REQUIRED");
        proofType = required(proofType, "TRUSTED_PROOF_TYPE_REQUIRED").toUpperCase();
        if (source == null) throw new IllegalArgumentException("TRUSTED_PROOF_SOURCE_REQUIRED");
        externalRunId = value(externalRunId);
        commandHash = optionalHash(commandHash, "TRUSTED_PROOF_COMMAND_HASH_INVALID");
        scriptHash = optionalHash(scriptHash, "TRUSTED_PROOF_SCRIPT_HASH_INVALID");
        resultStatus = resultStatus == null ? TrustedProofStatus.UNKNOWN : resultStatus;
        riskLevel = required(riskLevel, "TRUSTED_PROOF_RISK_LEVEL_REQUIRED").toUpperCase();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdBy = value(createdBy);
        createdAt = required(createdAt, "TRUSTED_PROOF_CREATED_AT_REQUIRED");
    }

    public boolean matches(TrustedProofCriteria criteria) {
        if (criteria == null || !resultStatus.passed()) return false;
        if (!projectId.equals(criteria.projectId())) return false;
        if (!packageId.equals(criteria.packageId())) return false;
        if (packageVersion != criteria.packageVersion()) return false;
        if (!packageHash.equals(criteria.packageHash())) return false;
        if (!riskLevel.equals(criteria.riskLevel())) return false;
        if (!proofType.equals(criteria.proofType())) return false;
        String external = criteria.externalRunIdOrProofId();
        return external.isBlank() || external.equals(proofId) || external.equals(externalRunId);
    }

    private static String optionalHash(String value, String error) {
        return value(value);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
