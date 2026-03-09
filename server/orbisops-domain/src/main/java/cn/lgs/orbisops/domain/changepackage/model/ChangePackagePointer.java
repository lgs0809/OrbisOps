package cn.lgs.orbisops.domain.changepackage.model;

public record ChangePackagePointer(String packageId,
                                   ChangePackageStatus status,
                                   int version,
                                   String packageHash,
                                   int approvedVersion,
                                   String approvedPackageHash) {

    public ChangePackagePointer {
        packageId = required(packageId, "CHANGE_PACKAGE_POINTER_PACKAGE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_STATUS_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_VERSION_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_POINTER_HASH_REQUIRED");
        if (approvedVersion < 0 || approvedVersion > version) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_APPROVED_VERSION_INVALID");
        }
        approvedPackageHash = text(approvedPackageHash);
        if ((approvedVersion == 0) != approvedPackageHash.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_APPROVAL_PAIR_INCOMPLETE");
        }
        boolean approved = approvedVersion > 0;
        if (requiresApprovedPointer(status) && !approved) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_APPROVAL_REQUIRED:" + status.name());
        }
        if (forbidsApprovedPointer(status) && approved) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_POINTER_STALE_APPROVAL:" + status.name());
        }
    }

    public boolean approved() {
        return approvedVersion > 0;
    }

    public ChangePackagePointer withStatus(ChangePackageStatus nextStatus) {
        return new ChangePackagePointer(packageId, nextStatus, version, packageHash,
                approvedVersion, approvedPackageHash);
    }

    private static boolean requiresApprovedPointer(ChangePackageStatus status) {
        return switch (status) {
            case APPROVED, LANDING_RUNNING, LANDED, LANDING_FAILED, NEEDS_REPLAN -> true;
            default -> false;
        };
    }

    private static boolean forbidsApprovedPointer(ChangePackageStatus status) {
        return switch (status) {
            case DRAFT, VALIDATING, VALIDATION_FAILED, REVISING,
                    READY_FOR_REVIEW, REVIEWING, REJECTED -> true;
            default -> false;
        };
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
