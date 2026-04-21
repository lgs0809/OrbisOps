package cn.lgs.orbisops.domain.repair.model;

import java.util.List;

public record RepairDiffSnapshot(
        String workspaceId,
        String baseCommit,
        String currentHead,
        List<String> changedFiles,
        String diffSummary,
        String diffHash,
        int diffBytes) {

    public RepairDiffSnapshot {
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        baseCommit = commit(baseCommit);
        currentHead = commit(currentHead);
        changedFiles = changedFiles == null ? List.of() : changedFiles.stream()
                .map(RepairDiffSnapshot::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        diffSummary = value(diffSummary);
        diffHash = required(diffHash, "REPAIR_DIFF_HASH_REQUIRED").toLowerCase();
        if (!diffHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("REPAIR_DIFF_HASH_INVALID");
        diffBytes = Math.max(0, diffBytes);
    }

    private static String commit(String value) {
        String normalized = required(value, "REPAIR_COMMIT_REQUIRED").toLowerCase();
        if (!normalized.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("REPAIR_COMMIT_INVALID");
        return normalized;
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
