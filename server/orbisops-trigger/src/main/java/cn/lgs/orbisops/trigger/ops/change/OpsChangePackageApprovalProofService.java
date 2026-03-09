package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;

/** Authoritative trusted-proof adapter for ChangePackage approval. */
final class OpsChangePackageApprovalProofService {

    private final OpsTrustedProofService trustedProofService;

    OpsChangePackageApprovalProofService(OpsTrustedProofService trustedProofService) {
        this.trustedProofService = trustedProofService;
    }

    boolean hasRepairProof(ChangePackageCurrent current,
                           ChangePackageVersion version,
                           String riskLevel,
                           String testProofHash,
                           String workspaceId) {
        return trusted(current, version, riskLevel, "CONTROLLED_BASH_TEST", testProofHash)
                || trusted(current, version, riskLevel, "CI_DRY_RUN", testProofHash)
                || trusted(current, version, riskLevel, "REPAIR_WORKSPACE_VERIFIED", workspaceId)
                || trusted(current, version, riskLevel, "VALIDATION_PROOF_WRITEBACK", "");
    }

    boolean trusted(ChangePackageCurrent current,
                    ChangePackageVersion version,
                    String riskLevel,
                    String proofType,
                    String externalRunOrProofId) {
        if (trustedProofService == null) return false;
        if (current == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REQUIRED");
        if (version == null) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_REQUIRED");
        return trustedProofService.verifyTrustedProof(
                current.projectId(),
                version.packageId(),
                version.version(),
                version.packageHash(),
                normalizeRisk(riskLevel),
                text(proofType),
                text(externalRunOrProofId));
    }

    private String normalizeRisk(String value) {
        String normalized = text(value).toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? "MEDIUM" : normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
