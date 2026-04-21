package cn.lgs.orbisops.domain.repair.model;

public record RepairVerificationResult(
        RepairDiffSnapshot diff,
        String repairCommit,
        String testProofHash,
        RepairWorkspaceStatus status,
        String verifiedBy) {

    public RepairVerificationResult {
        if (diff == null) throw new IllegalArgumentException("REPAIR_DIFF_REQUIRED");
        repairCommit = required(repairCommit, "REPAIR_COMMIT_REQUIRED").toLowerCase();
        testProofHash = required(testProofHash, "REPAIR_TEST_PROOF_REQUIRED");
        if (status != RepairWorkspaceStatus.VERIFIED) {
            throw new IllegalArgumentException("REPAIR_VERIFY_STATUS_INVALID");
        }
        verifiedBy = required(verifiedBy, "REPAIR_ACTOR_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
