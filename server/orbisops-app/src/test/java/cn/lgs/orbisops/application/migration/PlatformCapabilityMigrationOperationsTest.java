package cn.lgs.orbisops.application.migration;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCapabilityMigrationOperationsTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void completedMigrationMustBePersistedAndReusedWithoutRerunningContributor() {
        AtomicInteger inspections = new AtomicInteger();
        PlatformCapabilityMigrationContributor contributor = contributor(inspections, false);
        InMemoryRunPort runs = new InMemoryRunPort();
        PlatformCapabilityMigrationOperations operations = operations(contributor, runs);
        PlatformCapabilityMigrationCommand command = command("migration-1");

        PlatformCapabilityMigrationRunPort.RunSnapshot first = operations.execute(command);
        PlatformCapabilityMigrationRunPort.RunSnapshot second = operations.execute(command);

        assertEquals("COMPLETED", first.status());
        assertTrue(first.successful());
        assertEquals(first.reportHash(), second.reportHash());
        assertEquals(1, inspections.get());
        assertEquals(
                operations.view(first),
                operations.view(operations.require("migration-1")));
        assertEquals(1, operations.list(10).size());
    }

    @Test
    void sameMigrationIdWithDifferentCommandMustFailClosed() {
        InMemoryRunPort runs = new InMemoryRunPort();
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), false), runs);
        operations.execute(command("migration-1"));

        SecurityException error = assertThrows(SecurityException.class, () ->
                operations.execute(new PlatformCapabilityMigrationCommand(
                        "migration-1", "operator", true, 999,
                        List.of(PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE))));

        assertEquals("PLATFORM_MIGRATION_COMMAND_CONFLICT:migration-1", error.getMessage());
    }

    @Test
    void runningMigrationMustRejectConcurrentExecution() {
        InMemoryRunPort runs = new InMemoryRunPort();
        runs.forceInProgress = true;
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), false), runs);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> operations.execute(command("migration-running")));

        assertEquals("PLATFORM_MIGRATION_ALREADY_RUNNING:migration-running", error.getMessage());
    }

    @Test
    void contributorFailureMustRemainACompletedFailedReport() {
        InMemoryRunPort runs = new InMemoryRunPort();
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), true), runs);

        PlatformCapabilityMigrationRunPort.RunSnapshot failedReport =
                operations.execute(command("migration-step-failed"));

        assertEquals("COMPLETED", failedReport.status());
        assertEquals(false, failedReport.successful());
        assertEquals("FAILED", ((Map<?, ?>) ((List<?>) failedReport.report().get("steps"))
                .get(0)).get("status"));
    }

    @Test
    void reportPersistenceFailureMustBeMarkedAsRunFailureBeforeErrorEscapes() {
        InMemoryRunPort runs = new InMemoryRunPort();
        runs.forceCompleteFailure = true;
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), false), runs);

        assertThrows(IllegalStateException.class,
                () -> operations.execute(command("migration-persistence-failed")));

        PlatformCapabilityMigrationRunPort.RunSnapshot failed =
                operations.require("migration-persistence-failed");
        assertEquals("FAILED", failed.status());
        assertEquals("PLATFORM_MIGRATION_EXECUTION_FAILED", failed.errorCode());
    }

    @Test
    void releaseReadinessMustRequireSuccessfulFullDryRun() {
        InMemoryRunPort runs = new InMemoryRunPort();
        runs.values.put("release-scan", releaseSnapshot(
                "release-scan", true, true, false,
                List.of(PlatformCapabilityMigrationStep.values())));
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), false), runs);

        PlatformCapabilityMigrationOperations.ReleaseReadiness readiness =
                operations.releaseReadiness("release-scan");

        assertTrue(readiness.ready());
        assertEquals(List.of(), readiness.reasonCodes());
    }

    @Test
    void partialOrManualMigrationMustNeverOpenReleaseGate() {
        InMemoryRunPort runs = new InMemoryRunPort();
        runs.values.put("partial-scan", releaseSnapshot(
                "partial-scan", false, false, true,
                List.of(PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE)));
        PlatformCapabilityMigrationOperations operations = operations(
                contributor(new AtomicInteger(), false), runs);

        PlatformCapabilityMigrationOperations.ReleaseReadiness readiness =
                operations.releaseReadiness("partial-scan");

        assertEquals(false, readiness.ready());
        assertTrue(readiness.reasonCodes().contains(
                "PLATFORM_MIGRATION_RELEASE_GATE_REQUIRES_DRY_RUN"));
        assertTrue(readiness.reasonCodes().contains(
                "PLATFORM_MIGRATION_REPORT_NOT_SUCCESSFUL"));
        assertTrue(readiness.reasonCodes().contains(
                "PLATFORM_MIGRATION_MANUAL_REVIEW_REQUIRED"));
        assertTrue(readiness.reasonCodes().contains(
                "PLATFORM_MIGRATION_ALL_STEPS_REQUIRED"));
    }

    private PlatformCapabilityMigrationRunPort.RunSnapshot releaseSnapshot(
            String migrationId,
            boolean dryRun,
            boolean successful,
            boolean manualReview,
            List<PlatformCapabilityMigrationStep> steps) {
        return new PlatformCapabilityMigrationRunPort.RunSnapshot(
                migrationId,
                "a".repeat(64),
                "operator",
                dryRun,
                500,
                steps,
                "COMPLETED",
                1L,
                null,
                successful,
                manualReview,
                "b".repeat(64),
                Map.of("successful", successful),
                "",
                "",
                CLOCK.instant(),
                CLOCK.instant(),
                CLOCK.instant());
    }

    private PlatformCapabilityMigrationOperations operations(
            PlatformCapabilityMigrationContributor contributor,
            InMemoryRunPort runs) {
        return new PlatformCapabilityMigrationOperations(
                new PlatformCapabilityMigrationOrchestrator(List.of(contributor), CLOCK),
                runs,
                CLOCK,
                () -> "owner-1");
    }

    private PlatformCapabilityMigrationCommand command(String id) {
        return new PlatformCapabilityMigrationCommand(
                id,
                "operator",
                true,
                100,
                List.of(PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE));
    }

    private PlatformCapabilityMigrationContributor contributor(
            AtomicInteger inspections,
            boolean fail) {
        return new PlatformCapabilityMigrationContributor() {
            @Override
            public PlatformCapabilityMigrationStep step() {
                return PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE;
            }

            @Override
            public PlatformCapabilityMigrationInspection inspect(
                    PlatformCapabilityMigrationCommand command) {
                inspections.incrementAndGet();
                if (fail) throw new IllegalStateException("database unavailable");
                return new PlatformCapabilityMigrationInspection(
                        step(), 1, 1, 0, 0, true, true, List.of("CURRENT"));
            }

            @Override
            public PlatformCapabilityMigrationBackfillOutcome backfill(
                    PlatformCapabilityMigrationCommand command,
                    PlatformCapabilityMigrationInspection inspection) {
                return PlatformCapabilityMigrationBackfillOutcome.none(step());
            }
        };
    }

    private static final class InMemoryRunPort
            implements PlatformCapabilityMigrationRunPort {
        private final Map<String, RunSnapshot> values = new LinkedHashMap<>();
        private final Map<String, ClaimCommand> claims = new LinkedHashMap<>();
        private boolean forceInProgress;
        private boolean forceCompleteFailure;

        @Override
        public Claim claim(ClaimCommand command) {
            ClaimCommand existingCommand = claims.get(command.migrationId());
            RunSnapshot existing = values.get(command.migrationId());
            if (existingCommand != null
                    && !existingCommand.commandHash().equals(command.commandHash())) {
                return Claim.rejected(ClaimDisposition.CONFLICT, existing);
            }
            if (existing != null && "COMPLETED".equals(existing.status())) {
                return Claim.completed(existing);
            }
            if (forceInProgress) {
                claims.putIfAbsent(command.migrationId(), command);
                values.putIfAbsent(command.migrationId(), running(command));
                return Claim.rejected(
                        ClaimDisposition.IN_PROGRESS,
                        values.get(command.migrationId()));
            }
            claims.put(command.migrationId(), command);
            values.put(command.migrationId(), running(command));
            return Claim.acquired(1L);
        }

        @Override
        public void complete(CompleteCommand command) {
            if (forceCompleteFailure) {
                forceCompleteFailure = false;
                throw new IllegalStateException("report store unavailable");
            }
            ClaimCommand claim = claims.get(command.migrationId());
            values.put(command.migrationId(), new RunSnapshot(
                    command.migrationId(), claim.commandHash(), claim.actor(), claim.dryRun(),
                    claim.batchSize(), claim.steps(), "COMPLETED", command.fencingToken(), null,
                    command.report().successful(), command.report().manualReviewRequired(),
                    command.report().reportHash(), command.reportView(), "", "",
                    command.report().startedAt(), command.report().finishedAt(),
                    command.report().finishedAt()));
        }

        @Override
        public void fail(FailCommand command) {
            ClaimCommand claim = claims.get(command.migrationId());
            values.put(command.migrationId(), new RunSnapshot(
                    command.migrationId(), claim.commandHash(), claim.actor(), claim.dryRun(),
                    claim.batchSize(), claim.steps(), "FAILED", command.fencingToken(), null,
                    false, false, "", Map.of(), command.errorCode(), command.errorMessage(),
                    claim.claimedAt(), command.failedAt(), command.failedAt()));
        }

        @Override
        public Optional<RunSnapshot> find(String migrationId) {
            return Optional.ofNullable(values.get(migrationId));
        }

        @Override
        public List<RunSnapshot> list(int limit) {
            return new ArrayList<>(values.values()).stream().limit(limit).toList();
        }

        private RunSnapshot running(ClaimCommand command) {
            return new RunSnapshot(
                    command.migrationId(), command.commandHash(), command.actor(), command.dryRun(),
                    command.batchSize(), command.steps(), "RUNNING", 1L,
                    command.leaseExpiresAt(), false, false, "", Map.of(), "", "",
                    command.claimedAt(), null, command.claimedAt());
        }
    }
}
