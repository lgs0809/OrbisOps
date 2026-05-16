package cn.lgs.orbisops.application.migration;

public interface PlatformCapabilityMigrationContributor {

    PlatformCapabilityMigrationStep step();

    PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command);

    PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection);
}
