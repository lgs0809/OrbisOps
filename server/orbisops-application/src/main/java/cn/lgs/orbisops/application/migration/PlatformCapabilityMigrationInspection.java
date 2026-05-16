package cn.lgs.orbisops.application.migration;

import java.util.List;

public record PlatformCapabilityMigrationInspection(
        PlatformCapabilityMigrationStep step,
        long totalRows,
        long currentRows,
        long backfillableRows,
        long manualReviewRows,
        boolean dualReadReady,
        boolean newWriteReady,
        List<String> reasonCodes
) {

    public PlatformCapabilityMigrationInspection {
        if (step == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_STEP_REQUIRED");
        if (totalRows < 0 || currentRows < 0 || backfillableRows < 0 || manualReviewRows < 0
                || currentRows + backfillableRows + manualReviewRows != totalRows) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_INSPECTION_COUNT_INVALID");
        }
        reasonCodes = reasons(reasonCodes);
    }

    public boolean current() {
        return currentRows == totalRows && backfillableRows == 0 && manualReviewRows == 0;
    }

    public static PlatformCapabilityMigrationInspection failed(
            PlatformCapabilityMigrationStep step,
            String reasonCode) {
        return new PlatformCapabilityMigrationInspection(
                step, 0, 0, 0, 0, false, false, List.of(reasonCode));
    }

    private static List<String> reasons(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
