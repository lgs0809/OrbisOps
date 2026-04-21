package cn.lgs.orbisops.domain.repair.model;

public record RepairCommitResult(
        RepairDiffSnapshot diff,
        String repairCommit,
        RepairWorkspaceStatus status,
        String createdBy) {

    public RepairCommitResult {
        if (diff == null) throw new IllegalArgumentException("REPAIR_DIFF_REQUIRED");
        repairCommit = commit(repairCommit);
        if (status != RepairWorkspaceStatus.COMMITTED) {
            throw new IllegalArgumentException("REPAIR_COMMIT_STATUS_INVALID");
        }
        createdBy = required(createdBy, "REPAIR_ACTOR_REQUIRED");
    }

    private static String commit(String value) {
        String normalized = required(value, "REPAIR_COMMIT_REQUIRED").toLowerCase();
        if (!normalized.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("REPAIR_COMMIT_INVALID");
        return normalized;
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
