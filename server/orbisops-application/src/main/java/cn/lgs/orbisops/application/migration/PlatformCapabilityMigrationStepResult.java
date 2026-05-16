package cn.lgs.orbisops.application.migration;

import java.util.List;

public record PlatformCapabilityMigrationStepResult(
        PlatformCapabilityMigrationStep step,
        PlatformCapabilityMigrationStatus status,
        PlatformCapabilityMigrationInspection before,
        PlatformCapabilityMigrationBackfillOutcome backfill,
        PlatformCapabilityMigrationInspection after,
        long durationMs,
        List<String> reasonCodes
) {

    public PlatformCapabilityMigrationStepResult {
        if (step == null || status == null || before == null || backfill == null || after == null) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_STEP_RESULT_REQUIRED");
        }
        if (before.step() != step || backfill.step() != step || after.step() != step) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_STEP_RESULT_MISMATCH");
        }
        if (durationMs < 0) throw new IllegalArgumentException("PLATFORM_MIGRATION_DURATION_INVALID");
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
