package cn.lgs.orbisops.domain.repair.model;

public record RepairWriterLease(
        String workspaceId,
        String projectId,
        String ownerId,
        String leaseToken,
        long fencingToken,
        String expiresAt,
        long stateVersion) {

    public RepairWriterLease {
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        projectId = required(projectId, "REPAIR_PROJECT_ID_REQUIRED");
        ownerId = required(ownerId, "REPAIR_WRITER_OWNER_REQUIRED");
        leaseToken = required(leaseToken, "REPAIR_WRITER_TOKEN_REQUIRED");
        if (fencingToken < 1) throw new IllegalArgumentException("REPAIR_FENCING_TOKEN_INVALID");
        expiresAt = required(expiresAt, "REPAIR_WRITER_EXPIRES_AT_REQUIRED");
        if (stateVersion < 1) throw new IllegalArgumentException("REPAIR_STATE_VERSION_INVALID");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
