package cn.lgs.orbisops.domain.changepackage.model;

import java.time.Instant;

/** Authoritative current row for a ChangePackage aggregate. */
public record ChangePackageCurrent(long id,
                                   ChangePackagePointer pointer,
                                   String sessionId,
                                   String incidentId,
                                   String projectId,
                                   String preparationAgentId,
                                   int preparationAgentVersion,
                                   ChangePackageType packageType,
                                   ChangePackageCurrentState state,
                                   ChangePackageSnapshot approvedSnapshot,
                                   String landingRunId,
                                   String createBy,
                                   String approveBy,
                                   Instant createTime,
                                   Instant updateTime,
                                   Instant approvedAt) {

    public ChangePackageCurrent {
        if (id < 0) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_ID_INVALID");
        if (pointer == null) throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_REQUIRED");
        sessionId = text(sessionId);
        incidentId = text(incidentId);
        projectId = required(projectId, "CHANGE_PACKAGE_PROJECT_ID_REQUIRED");
        preparationAgentId = text(preparationAgentId);
        if (preparationAgentVersion < 0) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_AGENT_VERSION_INVALID");
        }
        if (packageType == null) throw new IllegalArgumentException("CHANGE_PACKAGE_TYPE_REQUIRED");
        if (state == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_STATE_REQUIRED");
        if (pointer.approved()) {
            if (approvedSnapshot == null) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVED_SNAPSHOT_REQUIRED");
            }
            if (!pointer.approvedPackageHash().equals(approvedSnapshot.packageHash())) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVED_SNAPSHOT_HASH_MISMATCH");
            }
            approvedSnapshot = new ChangePackageSnapshot(
                    approvedSnapshot.toMap(), pointer.approvedPackageHash());
        } else if (approvedSnapshot != null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_UNAPPROVED_SNAPSHOT_FORBIDDEN");
        }
        landingRunId = text(landingRunId);
        createBy = text(createBy);
        approveBy = text(approveBy);
    }

    public static ChangePackageCurrent draft(ChangePackagePointer pointer,
                                              String sessionId,
                                              String incidentId,
                                              String projectId,
                                              String preparationAgentId,
                                              int preparationAgentVersion,
                                              ChangePackageType packageType,
                                              ChangePackageCurrentState state,
                                              String actor) {
        return new ChangePackageCurrent(0, pointer, sessionId, incidentId, projectId,
                preparationAgentId, preparationAgentVersion, packageType, state,
                null, "", actor, "", null, null, null);
    }

    public String packageId() {
        return pointer.packageId();
    }

    public ChangePackageStatus status() {
        return pointer.status();
    }

    public int version() {
        return pointer.version();
    }

    public String packageHash() {
        return pointer.packageHash();
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
