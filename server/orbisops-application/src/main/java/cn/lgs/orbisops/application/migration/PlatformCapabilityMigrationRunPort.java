package cn.lgs.orbisops.application.migration;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable single-flight and report store for controlled platform migrations. */
public interface PlatformCapabilityMigrationRunPort {

    Claim claim(ClaimCommand command);

    void complete(CompleteCommand command);

    void fail(FailCommand command);

    Optional<RunSnapshot> find(String migrationId);

    List<RunSnapshot> list(int limit);

    enum ClaimDisposition {
        ACQUIRED,
        COMPLETED,
        IN_PROGRESS,
        CONFLICT
    }

    record ClaimCommand(
            String migrationId,
            String commandHash,
            String actor,
            boolean dryRun,
            int batchSize,
            List<PlatformCapabilityMigrationStep> steps,
            String ownerToken,
            Instant claimedAt,
            Instant leaseExpiresAt) {

        public ClaimCommand {
            migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
            commandHash = hash(commandHash, "PLATFORM_MIGRATION_COMMAND_HASH_INVALID");
            actor = required(actor, "PLATFORM_MIGRATION_ACTOR_REQUIRED");
            if (batchSize <= 0 || batchSize > 10_000) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_BATCH_SIZE_INVALID");
            }
            steps = steps == null ? List.of() : List.copyOf(steps);
            if (steps.isEmpty()) throw new IllegalArgumentException("PLATFORM_MIGRATION_STEPS_REQUIRED");
            ownerToken = required(ownerToken, "PLATFORM_MIGRATION_OWNER_REQUIRED");
            if (claimedAt == null || leaseExpiresAt == null || !leaseExpiresAt.isAfter(claimedAt)) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_LEASE_INVALID");
            }
        }
    }

    record Claim(ClaimDisposition disposition, long fencingToken, RunSnapshot snapshot) {
        public Claim {
            if (disposition == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_CLAIM_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            if (disposition == ClaimDisposition.COMPLETED && snapshot == null) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_COMPLETED_SNAPSHOT_REQUIRED");
            }
        }

        public static Claim acquired(long fencingToken) {
            return new Claim(ClaimDisposition.ACQUIRED, fencingToken, null);
        }

        public static Claim completed(RunSnapshot snapshot) {
            return new Claim(ClaimDisposition.COMPLETED, snapshot.fencingToken(), snapshot);
        }

        public static Claim rejected(ClaimDisposition disposition, RunSnapshot snapshot) {
            if (disposition == ClaimDisposition.ACQUIRED || disposition == ClaimDisposition.COMPLETED) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_REJECTION_INVALID");
            }
            return new Claim(disposition, snapshot == null ? 0L : snapshot.fencingToken(), snapshot);
        }
    }

    record CompleteCommand(
            String migrationId,
            String ownerToken,
            long fencingToken,
            PlatformCapabilityMigrationReport report,
            Map<String, Object> reportView) {

        public CompleteCommand {
            migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
            ownerToken = required(ownerToken, "PLATFORM_MIGRATION_OWNER_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            if (report == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_REPORT_REQUIRED");
            reportView = immutable(reportView);
        }
    }

    record FailCommand(
            String migrationId,
            String ownerToken,
            long fencingToken,
            String errorCode,
            String errorMessage,
            Instant failedAt) {

        public FailCommand {
            migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
            ownerToken = required(ownerToken, "PLATFORM_MIGRATION_OWNER_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            errorCode = required(errorCode, "PLATFORM_MIGRATION_ERROR_CODE_REQUIRED");
            errorMessage = value(errorMessage);
            if (failedAt == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_FAILED_AT_REQUIRED");
        }
    }

    record RunSnapshot(
            String migrationId,
            String commandHash,
            String actor,
            boolean dryRun,
            int batchSize,
            List<PlatformCapabilityMigrationStep> steps,
            String status,
            long fencingToken,
            Instant leaseExpiresAt,
            boolean successful,
            boolean manualReviewRequired,
            String reportHash,
            Map<String, Object> report,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant updatedAt) {

        public RunSnapshot {
            migrationId = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
            commandHash = hash(commandHash, "PLATFORM_MIGRATION_COMMAND_HASH_INVALID");
            actor = required(actor, "PLATFORM_MIGRATION_ACTOR_REQUIRED");
            batchSize = Math.max(1, batchSize);
            steps = steps == null ? List.of() : List.copyOf(steps);
            status = required(status, "PLATFORM_MIGRATION_STATUS_REQUIRED");
            fencingToken = Math.max(0L, fencingToken);
            reportHash = value(reportHash);
            report = immutable(report);
            errorCode = value(errorCode);
            errorMessage = value(errorMessage);
            if (startedAt == null || updatedAt == null) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_SNAPSHOT_TIME_REQUIRED");
            }
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String hash(String value, String error) {
        String normalized = value(value).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
