package cn.lgs.orbisops.application.migration;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PlatformCapabilityMigrationReport(
        String migrationId,
        Instant startedAt,
        Instant finishedAt,
        List<PlatformCapabilityMigrationStepResult> results,
        String reportHash
) {

    public PlatformCapabilityMigrationReport {
        migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_TIME_INVALID");
        }
        results = results == null ? List.of() : List.copyOf(results);
        if (results.isEmpty()) throw new IllegalArgumentException("PLATFORM_MIGRATION_RESULTS_REQUIRED");
        String expected = calculateHash(migrationId, startedAt, finishedAt, results);
        reportHash = reportHash == null || reportHash.isBlank() ? expected : reportHash.trim();
        if (!expected.equals(reportHash)) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_REPORT_HASH_MISMATCH");
        }
    }

    public boolean successful() {
        return results.stream().noneMatch(result ->
                result.status() == PlatformCapabilityMigrationStatus.FAILED
                        || result.status() == PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED);
    }

    public boolean manualReviewRequired() {
        return results.stream().anyMatch(result ->
                result.status() == PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED);
    }

    public static String calculateHash(
            String migrationId,
            Instant startedAt,
            Instant finishedAt,
            List<PlatformCapabilityMigrationStepResult> results) {
        return CanonicalObjectHasher.sha256(Map.of(
                "migrationId", migrationId,
                "startedAt", startedAt.toString(),
                "finishedAt", finishedAt.toString(),
                "results", results));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
