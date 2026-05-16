package cn.lgs.orbisops.application.migration;

public enum PlatformCapabilityMigrationStatus {
    ALREADY_CURRENT,
    DUAL_READ_READY,
    DRY_RUN_READY,
    BACKFILLED,
    MANUAL_REVIEW_REQUIRED,
    FAILED
}
