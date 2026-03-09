package cn.lgs.orbisops.domain.repair.model;

import java.util.Locale;

public enum RepairWorkspaceStatus {
    PREPARING,
    ACTIVE,
    DIRTY,
    COMMITTED,
    TESTING,
    TEST_PASSED,
    VERIFIED,
    TEST_FAILED,
    BUILD_MUTATED_SOURCE,
    ARTIFACT_MISSING,
    FAILED,
    CLEANED;

    public static RepairWorkspaceStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("REPAIR_WORKSPACE_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("REPAIR_WORKSPACE_STATUS_UNKNOWN:" + normalized);
        }
    }

    public boolean terminalFailure() {
        return this == TEST_FAILED || this == BUILD_MUTATED_SOURCE
                || this == ARTIFACT_MISSING || this == FAILED;
    }
}
