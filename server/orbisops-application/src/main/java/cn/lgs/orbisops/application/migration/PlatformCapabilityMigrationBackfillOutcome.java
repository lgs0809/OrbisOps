package cn.lgs.orbisops.application.migration;

import java.util.List;

public record PlatformCapabilityMigrationBackfillOutcome(
        PlatformCapabilityMigrationStep step,
        long attemptedRows,
        long backfilledRows,
        long manualReviewRows,
        long failedRows,
        List<String> reasonCodes
) {

    public PlatformCapabilityMigrationBackfillOutcome {
        if (step == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_STEP_REQUIRED");
        if (attemptedRows < 0 || backfilledRows < 0 || manualReviewRows < 0 || failedRows < 0
                || backfilledRows + manualReviewRows + failedRows != attemptedRows) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_BACKFILL_COUNT_INVALID");
        }
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static PlatformCapabilityMigrationBackfillOutcome none(
            PlatformCapabilityMigrationStep step) {
        return new PlatformCapabilityMigrationBackfillOutcome(step, 0, 0, 0, 0, List.of());
    }
}
