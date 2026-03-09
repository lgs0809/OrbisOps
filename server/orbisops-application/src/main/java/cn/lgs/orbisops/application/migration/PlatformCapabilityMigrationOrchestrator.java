package cn.lgs.orbisops.application.migration;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Ordered, idempotent migration coordinator. It never treats manual review as success. */
public final class PlatformCapabilityMigrationOrchestrator {

    private final Map<PlatformCapabilityMigrationStep, PlatformCapabilityMigrationContributor> contributors;
    private final Clock clock;

    public PlatformCapabilityMigrationOrchestrator(
            List<PlatformCapabilityMigrationContributor> contributors) {
        this(contributors, Clock.systemUTC());
    }

    PlatformCapabilityMigrationOrchestrator(
            List<PlatformCapabilityMigrationContributor> contributors,
            Clock clock) {
        if (clock == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_CLOCK_REQUIRED");
        EnumMap<PlatformCapabilityMigrationStep, PlatformCapabilityMigrationContributor> registry =
                new EnumMap<>(PlatformCapabilityMigrationStep.class);
        for (PlatformCapabilityMigrationContributor contributor
                : contributors == null ? List.<PlatformCapabilityMigrationContributor>of() : contributors) {
            if (contributor == null || contributor.step() == null) {
                throw new IllegalArgumentException("PLATFORM_MIGRATION_CONTRIBUTOR_INVALID");
            }
            if (registry.putIfAbsent(contributor.step(), contributor) != null) {
                throw new IllegalStateException(
                        "PLATFORM_MIGRATION_CONTRIBUTOR_DUPLICATE:" + contributor.step());
            }
        }
        this.contributors = Map.copyOf(registry);
        this.clock = clock;
    }

    public PlatformCapabilityMigrationReport migrate(
            PlatformCapabilityMigrationCommand command) {
        if (command == null) throw new IllegalArgumentException("PLATFORM_MIGRATION_COMMAND_REQUIRED");
        Instant startedAt = clock.instant();
        List<PlatformCapabilityMigrationStepResult> results = new ArrayList<>();
        for (PlatformCapabilityMigrationStep step : command.steps()) {
            results.add(migrateStep(step, command));
        }
        Instant finishedAt = clock.instant();
        String hash = PlatformCapabilityMigrationReport.calculateHash(
                command.migrationId(), startedAt, finishedAt, results);
        return new PlatformCapabilityMigrationReport(
                command.migrationId(), startedAt, finishedAt, results, hash);
    }

    private PlatformCapabilityMigrationStepResult migrateStep(
            PlatformCapabilityMigrationStep step,
            PlatformCapabilityMigrationCommand command) {
        long startedNanos = System.nanoTime();
        PlatformCapabilityMigrationContributor contributor = contributors.get(step);
        if (contributor == null) {
            return failed(step, startedNanos, "PLATFORM_MIGRATION_CONTRIBUTOR_MISSING:" + step);
        }
        try {
            PlatformCapabilityMigrationInspection before = requireInspection(
                    step, contributor.inspect(command));
            if (!before.dualReadReady() || !before.newWriteReady()) {
                return result(step, PlatformCapabilityMigrationStatus.FAILED, before,
                        PlatformCapabilityMigrationBackfillOutcome.none(step), before, startedNanos,
                        merge(before.reasonCodes(), "PLATFORM_MIGRATION_COMPATIBILITY_NOT_READY"));
            }
            if (before.current()) {
                return result(step, PlatformCapabilityMigrationStatus.ALREADY_CURRENT, before,
                        PlatformCapabilityMigrationBackfillOutcome.none(step), before, startedNanos,
                        before.reasonCodes());
            }
            if (before.backfillableRows() == 0) {
                PlatformCapabilityMigrationStatus status = before.manualReviewRows() > 0
                        ? PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED
                        : PlatformCapabilityMigrationStatus.DUAL_READ_READY;
                return result(step, status, before,
                        PlatformCapabilityMigrationBackfillOutcome.none(step), before, startedNanos,
                        before.reasonCodes());
            }
            if (command.dryRun()) {
                return result(step, PlatformCapabilityMigrationStatus.DRY_RUN_READY, before,
                        PlatformCapabilityMigrationBackfillOutcome.none(step), before, startedNanos,
                        merge(before.reasonCodes(), "PLATFORM_MIGRATION_DRY_RUN"));
            }
            PlatformCapabilityMigrationBackfillOutcome outcome = requireBackfill(
                    step, contributor.backfill(command, before));
            PlatformCapabilityMigrationInspection after = requireInspection(
                    step, contributor.inspect(command));
            PlatformCapabilityMigrationStatus status;
            if (outcome.failedRows() > 0) {
                status = PlatformCapabilityMigrationStatus.FAILED;
            } else if (after.current()) {
                status = PlatformCapabilityMigrationStatus.BACKFILLED;
            } else if (after.manualReviewRows() > 0) {
                status = PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED;
            } else {
                status = PlatformCapabilityMigrationStatus.FAILED;
            }
            return result(step, status, before, outcome, after, startedNanos,
                    merge(before.reasonCodes(), outcome.reasonCodes(), after.reasonCodes()));
        } catch (RuntimeException error) {
            return failed(step, startedNanos,
                    "PLATFORM_MIGRATION_STEP_EXCEPTION:" + error.getClass().getSimpleName());
        }
    }

    private PlatformCapabilityMigrationStepResult failed(
            PlatformCapabilityMigrationStep step,
            long startedNanos,
            String reasonCode) {
        PlatformCapabilityMigrationInspection failed =
                PlatformCapabilityMigrationInspection.failed(step, reasonCode);
        return result(step, PlatformCapabilityMigrationStatus.FAILED, failed,
                PlatformCapabilityMigrationBackfillOutcome.none(step), failed,
                startedNanos, List.of(reasonCode));
    }

    private PlatformCapabilityMigrationStepResult result(
            PlatformCapabilityMigrationStep step,
            PlatformCapabilityMigrationStatus status,
            PlatformCapabilityMigrationInspection before,
            PlatformCapabilityMigrationBackfillOutcome backfill,
            PlatformCapabilityMigrationInspection after,
            long startedNanos,
            List<String> reasons) {
        return new PlatformCapabilityMigrationStepResult(
                step, status, before, backfill, after,
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L), reasons);
    }

    private PlatformCapabilityMigrationInspection requireInspection(
            PlatformCapabilityMigrationStep step,
            PlatformCapabilityMigrationInspection inspection) {
        if (inspection == null || inspection.step() != step) {
            throw new IllegalStateException("PLATFORM_MIGRATION_INSPECTION_MISMATCH");
        }
        return inspection;
    }

    private PlatformCapabilityMigrationBackfillOutcome requireBackfill(
            PlatformCapabilityMigrationStep step,
            PlatformCapabilityMigrationBackfillOutcome outcome) {
        if (outcome == null || outcome.step() != step) {
            throw new IllegalStateException("PLATFORM_MIGRATION_BACKFILL_MISMATCH");
        }
        return outcome;
    }

    @SafeVarargs
    private final List<String> merge(List<String>... reasonGroups) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (List<String> group : reasonGroups) {
            if (group != null) reasons.addAll(group);
        }
        return reasons.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .toList();
    }

    private List<String> merge(List<String> reasons, String reason) {
        return merge(reasons, List.of(reason));
    }
}
