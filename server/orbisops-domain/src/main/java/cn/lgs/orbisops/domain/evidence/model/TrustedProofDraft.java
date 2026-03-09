package cn.lgs.orbisops.domain.evidence.model;

import java.util.Map;

public record TrustedProofDraft(
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
        String actor) {

    public TrustedProofDraft {
        proofId = value(proofId);
        projectId = required(projectId, "trusted proof 必须绑定 projectId");
        packageId = required(packageId, "trusted proof 必须绑定 packageId");
        if (packageVersion <= 0) throw new IllegalArgumentException("trusted proof packageVersion 必须大于 0");
        packageHash = required(packageHash, "trusted proof 必须绑定 packageHash");
        proofType = required(proofType, "trusted proof 必须提供 proofType").toUpperCase();
        if (source == null) throw new IllegalArgumentException("TRUSTED_PROOF_SOURCE_REQUIRED");
        externalRunId = value(externalRunId);
        commandHash = optionalHash(commandHash, "TRUSTED_PROOF_COMMAND_HASH_INVALID");
        scriptHash = optionalHash(scriptHash, "TRUSTED_PROOF_SCRIPT_HASH_INVALID");
        resultStatus = resultStatus == null ? TrustedProofStatus.UNKNOWN : resultStatus;
        riskLevel = value(riskLevel).isBlank() ? "MEDIUM" : value(riskLevel).toUpperCase();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        actor = value(actor);
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
