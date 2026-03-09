package cn.lgs.orbisops.application.changepackage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Published cleanup facts translated from the Repair bounded context. */
public record ChangePackageRepairCleanupOutcome(
        String workspaceId,
        String status,
        boolean removed,
        String worktreePath
) {

    public ChangePackageRepairCleanupOutcome {
        workspaceId = required(workspaceId, "CHANGE_PACKAGE_CLEANUP_WORKSPACE_ID_REQUIRED");
        status = required(status, "CHANGE_PACKAGE_CLEANUP_STATUS_REQUIRED");
        worktreePath = worktreePath == null ? "" : worktreePath.trim();
    }

    public Map<String, Object> payload() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", workspaceId);
        result.put("status", status);
        result.put("removed", removed);
        if (!worktreePath.isBlank()) result.put("worktreePath", worktreePath);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
