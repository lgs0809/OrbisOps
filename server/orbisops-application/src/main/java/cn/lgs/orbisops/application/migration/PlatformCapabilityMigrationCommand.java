package cn.lgs.orbisops.application.migration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public record PlatformCapabilityMigrationCommand(
        String migrationId,
        String actor,
        boolean dryRun,
        int batchSize,
        List<PlatformCapabilityMigrationStep> steps
) {

    public PlatformCapabilityMigrationCommand {
        migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
        actor = required(actor, "PLATFORM_MIGRATION_ACTOR_REQUIRED");
        if (batchSize <= 0 || batchSize > 10_000) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_BATCH_SIZE_INVALID");
        }
        steps = steps == null || steps.isEmpty()
                ? List.copyOf(Arrays.asList(PlatformCapabilityMigrationStep.values()))
                : steps.stream().distinct().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
