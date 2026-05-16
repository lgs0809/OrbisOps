package cn.lgs.orbisops.application.migration;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Controlled single-flight operations boundary for dry-run and bounded migration execution. */
public final class PlatformCapabilityMigrationOperations {

    private static final Duration EXECUTION_LEASE = Duration.ofMinutes(15);

    private final PlatformCapabilityMigrationOrchestrator orchestrator;
    private final PlatformCapabilityMigrationRunPort runs;
    private final Clock clock;
    private final Supplier<String> ownerTokenSupplier;

    public PlatformCapabilityMigrationOperations(
            PlatformCapabilityMigrationOrchestrator orchestrator,
            PlatformCapabilityMigrationRunPort runs,
            Clock clock,
            Supplier<String> ownerTokenSupplier) {
        if (orchestrator == null || runs == null || clock == null || ownerTokenSupplier == null) {
            throw new IllegalArgumentException("PLATFORM_MIGRATION_OPERATIONS_DEPENDENCY_REQUIRED");
        }
        this.orchestrator = orchestrator;
        this.runs = runs;
        this.clock = clock;
        this.ownerTokenSupplier = ownerTokenSupplier;
    }

    public PlatformCapabilityMigrationRunPort.RunSnapshot execute(
            PlatformCapabilityMigrationCommand command) {
        if (command == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_COMMAND_REQUIRED");
        String commandHash = commandHash(command);
        String ownerToken = required(
                ownerTokenSupplier.get(), "PLATFORM_MIGRATION_OWNER_TOKEN_REQUIRED");
        Instant now = clock.instant();
        PlatformCapabilityMigrationRunPort.Claim claim = runs.claim(
                new PlatformCapabilityMigrationRunPort.ClaimCommand(
                        command.migrationId(),
                        commandHash,
                        command.actor(),
                        command.dryRun(),
                        command.batchSize(),
                        command.steps(),
                        ownerToken,
                        now,
                        now.plus(EXECUTION_LEASE)));
        switch (claim.disposition()) {
            case COMPLETED -> {
                return claim.snapshot();
            }
            case IN_PROGRESS -> throw new IllegalStateException(
                    "PLATFORM_MIGRATION_ALREADY_RUNNING:" + command.migrationId());
            case CONFLICT -> throw new SecurityException(
                    "PLATFORM_MIGRATION_COMMAND_CONFLICT:" + command.migrationId());
            case ACQUIRED -> {
                // Continue below.
            }
        }
        try {
            PlatformCapabilityMigrationReport report = orchestrator.migrate(command);
            Map<String, Object> reportView = reportView(report);
            runs.complete(new PlatformCapabilityMigrationRunPort.CompleteCommand(
                    command.migrationId(),
                    ownerToken,
                    claim.fencingToken(),
                    report,
                    reportView));
            return require(command.migrationId());
        } catch (RuntimeException error) {
            try {
                runs.fail(new PlatformCapabilityMigrationRunPort.FailCommand(
                        command.migrationId(),
                        ownerToken,
                        claim.fencingToken(),
                        "PLATFORM_MIGRATION_EXECUTION_FAILED",
                        message(error),
                        clock.instant()));
            } catch (RuntimeException persistenceFailure) {
                error.addSuppressed(persistenceFailure);
            }
            throw error;
        }
    }

    public PlatformCapabilityMigrationRunPort.RunSnapshot require(String migrationId) {
        String id = required(migrationId, "PLATFORM_MIGRATION_ID_REQUIRED");
        return runs.find(id).orElseThrow(() -> new IllegalArgumentException(
                "PLATFORM_MIGRATION_RUN_NOT_FOUND:" + id));
    }

    public List<PlatformCapabilityMigrationRunPort.RunSnapshot> list(int limit) {
        return List.copyOf(runs.list(Math.max(1, Math.min(limit, 200))));
    }

    public ReleaseReadiness releaseReadiness(String migrationId) {
        PlatformCapabilityMigrationRunPort.RunSnapshot snapshot = require(migrationId);
        List<String> reasons = new ArrayList<>();
        if (!"COMPLETED".equals(snapshot.status())) {
            reasons.add("PLATFORM_MIGRATION_NOT_COMPLETED");
        }
        if (!snapshot.dryRun()) {
            reasons.add("PLATFORM_MIGRATION_RELEASE_GATE_REQUIRES_DRY_RUN");
        }
        if (!snapshot.successful()) {
            reasons.add("PLATFORM_MIGRATION_REPORT_NOT_SUCCESSFUL");
        }
        if (snapshot.manualReviewRequired()) {
            reasons.add("PLATFORM_MIGRATION_MANUAL_REVIEW_REQUIRED");
        }
        Set<PlatformCapabilityMigrationStep> required = Set.copyOf(
                Arrays.asList(PlatformCapabilityMigrationStep.values()));
        if (!Set.copyOf(snapshot.steps()).equals(required)) {
            reasons.add("PLATFORM_MIGRATION_ALL_STEPS_REQUIRED");
        }
        if (!snapshot.reportHash().matches("[a-f0-9]{64}")) {
            reasons.add("PLATFORM_MIGRATION_REPORT_HASH_INVALID");
        }
        return new ReleaseReadiness(
                snapshot.migrationId(),
                reasons.isEmpty(),
                snapshot.reportHash(),
                reasons.stream().distinct().sorted().toList());
    }

    public Map<String, Object> view(
            PlatformCapabilityMigrationRunPort.RunSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_SNAPSHOT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("migrationId", snapshot.migrationId());
        result.put("actor", snapshot.actor());
        result.put("dryRun", snapshot.dryRun());
        result.put("batchSize", snapshot.batchSize());
        result.put("steps", snapshot.steps().stream().map(Enum::name).toList());
        result.put("status", snapshot.status());
        result.put("successful", snapshot.successful());
        result.put("manualReviewRequired", snapshot.manualReviewRequired());
        result.put("reportHash", snapshot.reportHash());
        result.put("report", snapshot.report());
        result.put("errorCode", snapshot.errorCode());
        result.put("errorMessage", snapshot.errorMessage());
        result.put("startedAt", snapshot.startedAt().toString());
        result.put("finishedAt", snapshot.finishedAt() == null ? "" : snapshot.finishedAt().toString());
        result.put("updatedAt", snapshot.updatedAt().toString());
        return Map.copyOf(result);
    }

    private String commandHash(PlatformCapabilityMigrationCommand command) {
        return CanonicalObjectHasher.sha256(Map.of(
                "migrationId", command.migrationId(),
                "actor", command.actor(),
                "dryRun", command.dryRun(),
                "batchSize", command.batchSize(),
                "steps", command.steps().stream().map(Enum::name).toList()));
    }

    private Map<String, Object> reportView(PlatformCapabilityMigrationReport report) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("migrationId", report.migrationId());
        result.put("startedAt", report.startedAt().toString());
        result.put("finishedAt", report.finishedAt().toString());
        result.put("successful", report.successful());
        result.put("manualReviewRequired", report.manualReviewRequired());
        result.put("reportHash", report.reportHash());
        result.put("steps", report.results().stream().map(this::stepView).toList());
        return Map.copyOf(result);
    }

    private Map<String, Object> stepView(PlatformCapabilityMigrationStepResult result) {
        return Map.of(
                "step", result.step().name(),
                "status", result.status().name(),
                "before", inspectionView(result.before()),
                "backfill", backfillView(result.backfill()),
                "after", inspectionView(result.after()),
                "durationMs", result.durationMs(),
                "reasonCodes", result.reasonCodes());
    }

    private Map<String, Object> inspectionView(
            PlatformCapabilityMigrationInspection inspection) {
        return Map.of(
                "totalRows", inspection.totalRows(),
                "currentRows", inspection.currentRows(),
                "backfillableRows", inspection.backfillableRows(),
                "manualReviewRows", inspection.manualReviewRows(),
                "dualReadReady", inspection.dualReadReady(),
                "newWriteReady", inspection.newWriteReady(),
                "reasonCodes", inspection.reasonCodes());
    }

    private Map<String, Object> backfillView(
            PlatformCapabilityMigrationBackfillOutcome outcome) {
        return Map.of(
                "attemptedRows", outcome.attemptedRows(),
                "backfilledRows", outcome.backfilledRows(),
                "manualReviewRows", outcome.manualReviewRows(),
                "failedRows", outcome.failedRows(),
                "reasonCodes", outcome.reasonCodes());
    }

    private String message(Throwable error) {
        String value = error == null ? "" : String.valueOf(error.getMessage()).trim();
        return value.isBlank() && error != null ? error.getClass().getSimpleName() : value;
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    public record ReleaseReadiness(
            String migrationId,
            boolean ready,
            String reportHash,
            List<String> reasonCodes) {

        public ReleaseReadiness {
            migrationId = migrationId == null ? "" : migrationId.trim();
            reportHash = reportHash == null ? "" : reportHash.trim();
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }

        public Map<String, Object> view() {
            return Map.of(
                    "migrationId", migrationId,
                    "ready", ready,
                    "reportHash", reportHash,
                    "reasonCodes", reasonCodes);
        }
    }
}
