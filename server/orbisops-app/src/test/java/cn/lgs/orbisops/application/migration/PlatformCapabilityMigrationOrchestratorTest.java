package cn.lgs.orbisops.application.migration;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformCapabilityMigrationOrchestratorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-02T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void duplicateContributorMustFailAtCompositionTime() {
        StatefulContributor first = new StatefulContributor(
                PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION, 0, 0);
        StatefulContributor duplicate = new StatefulContributor(
                PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION, 0, 0);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new PlatformCapabilityMigrationOrchestrator(
                        List.of(first, duplicate), CLOCK));

        assertTrue(error.getMessage().contains("PLATFORM_MIGRATION_CONTRIBUTOR_DUPLICATE"));
    }

    @Test
    void defaultStepsAndMigrationCountsMustBeStrictlyImmutableAndComplete() {
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                "migration-default", "operator", true, 100, null);

        assertEquals(List.of(PlatformCapabilityMigrationStep.values()), command.steps());
        assertThrows(UnsupportedOperationException.class, () ->
                command.steps().set(0, PlatformCapabilityMigrationStep.BOUND_WORKFLOW_SNAPSHOT));
        assertThrows(IllegalArgumentException.class, () ->
                new PlatformCapabilityMigrationInspection(
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        2, 1, 0, 0, true, true, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new PlatformCapabilityMigrationBackfillOutcome(
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        2, 1, 0, 0, List.of()));
    }

    @Test
    void commandMustRunInStableStepOrderAndDryRunMustNotBackfill() {
        List<PlatformCapabilityMigrationContributor> contributors = new ArrayList<>();
        for (PlatformCapabilityMigrationStep step : PlatformCapabilityMigrationStep.values()) {
            contributors.add(new StatefulContributor(step, 1, 0));
        }
        Collections.reverse(contributors);
        PlatformCapabilityMigrationOrchestrator orchestrator =
                new PlatformCapabilityMigrationOrchestrator(contributors, CLOCK);
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                "migration-1", "operator", true, 100,
                List.of(
                        PlatformCapabilityMigrationStep.BOUND_WORKFLOW_SNAPSHOT,
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION));

        PlatformCapabilityMigrationReport report = orchestrator.migrate(command);

        assertEquals(List.of(
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION,
                        PlatformCapabilityMigrationStep.BOUND_WORKFLOW_SNAPSHOT),
                report.results().stream().map(PlatformCapabilityMigrationStepResult::step).toList());
        assertTrue(report.results().stream().allMatch(result ->
                result.status() == PlatformCapabilityMigrationStatus.DRY_RUN_READY));
        assertTrue(contributors.stream()
                .map(StatefulContributor.class::cast)
                .allMatch(contributor -> contributor.backfillCalls == 0));
        assertTrue(report.reportHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void successfulBackfillMustBecomeAlreadyCurrentOnSecondRun() {
        StatefulContributor contributor = new StatefulContributor(
                PlatformCapabilityMigrationStep.TOOL_DEFINITION_SEMANTICS_PROVIDER, 2, 0);
        PlatformCapabilityMigrationOrchestrator orchestrator =
                new PlatformCapabilityMigrationOrchestrator(List.of(contributor), CLOCK);
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                "migration-2", "operator", false, 100,
                List.of(PlatformCapabilityMigrationStep.TOOL_DEFINITION_SEMANTICS_PROVIDER));

        PlatformCapabilityMigrationReport first = orchestrator.migrate(command);
        PlatformCapabilityMigrationReport second = orchestrator.migrate(command);

        assertEquals(PlatformCapabilityMigrationStatus.BACKFILLED,
                first.results().get(0).status());
        assertEquals(PlatformCapabilityMigrationStatus.ALREADY_CURRENT,
                second.results().get(0).status());
        assertEquals(1, contributor.backfillCalls);
    }

    @Test
    void manualReviewOnlyReportMustNotBeSuccessful() {
        StatefulContributor manual = new StatefulContributor(
                PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE, 0, 2);
        PlatformCapabilityMigrationOrchestrator orchestrator =
                new PlatformCapabilityMigrationOrchestrator(List.of(manual), CLOCK);
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                "migration-manual", "operator", false, 100,
                List.of(PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE));

        PlatformCapabilityMigrationReport report = orchestrator.migrate(command);

        assertEquals(PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED,
                report.results().get(0).status());
        assertTrue(report.manualReviewRequired());
        assertFalse(report.successful());
    }

    @Test
    void manualReviewMissingContributorAndExceptionMustRemainExplicit() {
        StatefulContributor manual = new StatefulContributor(
                PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE, 0, 2);
        PlatformCapabilityMigrationContributor broken = new PlatformCapabilityMigrationContributor() {
            @Override
            public PlatformCapabilityMigrationStep step() {
                return PlatformCapabilityMigrationStep.MCP_RUNTIME_SOURCE_CHAIN;
            }

            @Override
            public PlatformCapabilityMigrationInspection inspect(
                    PlatformCapabilityMigrationCommand command) {
                throw new IllegalStateException("offline");
            }

            @Override
            public PlatformCapabilityMigrationBackfillOutcome backfill(
                    PlatformCapabilityMigrationCommand command,
                    PlatformCapabilityMigrationInspection inspection) {
                throw new AssertionError("must not execute");
            }
        };
        PlatformCapabilityMigrationOrchestrator orchestrator =
                new PlatformCapabilityMigrationOrchestrator(List.of(manual, broken), CLOCK);
        PlatformCapabilityMigrationCommand command = new PlatformCapabilityMigrationCommand(
                "migration-3", "operator", false, 100,
                List.of(
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION,
                        PlatformCapabilityMigrationStep.MCP_RUNTIME_SOURCE_CHAIN));

        PlatformCapabilityMigrationReport report = orchestrator.migrate(command);

        assertEquals(PlatformCapabilityMigrationStatus.MANUAL_REVIEW_REQUIRED,
                report.results().get(0).status());
        assertEquals(PlatformCapabilityMigrationStatus.FAILED,
                report.results().get(1).status());
        assertTrue(report.results().get(1).reasonCodes().get(0)
                .contains("PLATFORM_MIGRATION_CONTRIBUTOR_MISSING"));
        assertEquals(PlatformCapabilityMigrationStatus.FAILED,
                report.results().get(2).status());
        assertTrue(report.results().get(2).reasonCodes().get(0)
                .contains("PLATFORM_MIGRATION_STEP_EXCEPTION"));
        assertFalse(report.successful());
        assertTrue(report.manualReviewRequired());
    }

    private static final class StatefulContributor
            implements PlatformCapabilityMigrationContributor {
        private final PlatformCapabilityMigrationStep step;
        private long backfillableRows;
        private final long manualRows;
        private int backfillCalls;

        private StatefulContributor(
                PlatformCapabilityMigrationStep step,
                long backfillableRows,
                long manualRows) {
            this.step = step;
            this.backfillableRows = backfillableRows;
            this.manualRows = manualRows;
        }

        @Override
        public PlatformCapabilityMigrationStep step() {
            return step;
        }

        @Override
        public PlatformCapabilityMigrationInspection inspect(
                PlatformCapabilityMigrationCommand command) {
            long total = 3;
            long current = Math.max(0, total - backfillableRows - manualRows);
            return new PlatformCapabilityMigrationInspection(
                    step, total, current, backfillableRows, manualRows,
                    true, true, manualRows > 0 ? List.of("MANUAL") : List.of());
        }

        @Override
        public PlatformCapabilityMigrationBackfillOutcome backfill(
                PlatformCapabilityMigrationCommand command,
                PlatformCapabilityMigrationInspection inspection) {
            backfillCalls++;
            long attempted = backfillableRows;
            backfillableRows = 0;
            return new PlatformCapabilityMigrationBackfillOutcome(
                    step, attempted, attempted, 0, 0, List.of());
        }
    }
}
