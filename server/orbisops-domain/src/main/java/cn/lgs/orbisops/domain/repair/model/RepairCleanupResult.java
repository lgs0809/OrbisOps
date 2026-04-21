package cn.lgs.orbisops.domain.repair.model;

public record RepairCleanupResult(
        String workspaceId,
        String status,
        boolean removed,
        String worktreePath) {

    public RepairCleanupResult {
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        status = required(status, "REPAIR_CLEANUP_STATUS_REQUIRED");
        worktreePath = worktreePath == null ? "" : worktreePath.trim();
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
