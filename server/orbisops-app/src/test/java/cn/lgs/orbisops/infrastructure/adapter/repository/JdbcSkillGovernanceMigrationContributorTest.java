package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationBackfillOutcome;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcSkillGovernanceMigrationContributorTest {

    @Test
    void missingStoreMustBeAnIdempotentNoDataState() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcSkillGovernanceMigrationContributor contributor =
                new JdbcSkillGovernanceMigrationContributor(provider);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertTrue(inspection.current());
        assertTrue(inspection.dualReadReady());
        assertTrue(inspection.newWriteReady());
        assertEquals(List.of("SKILL_GOVERNANCE_STORE_NOT_CONFIGURED"),
                inspection.reasonCodes());
    }

    @Test
    void configuredStoreWithoutSkillTableMustBlockMigration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Long.class)))
                .thenReturn(0L);
        JdbcSkillGovernanceMigrationContributor contributor = contributor(jdbc);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(0, inspection.totalRows());
        assertTrue(!inspection.dualReadReady());
        assertTrue(!inspection.newWriteReady());
        assertEquals(List.of("SKILL_GOVERNANCE_TABLE_MISSING"), inspection.reasonCodes());
    }

    @Test
    void inspectionMustSeparateCurrentBackfillableAndManualRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                contains("information_schema.columns"), eq(Long.class), any(Object[].class)))
                .thenReturn(10L);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM ai_ops_skill"), eq(Long.class)))
                .thenReturn(3L);
        when(jdbc.queryForObject(
                contains("legacy_frozen_classification_required=1"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                contains("UPPER(lifecycle_status) NOT IN"), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.queryForObject(
                contains("OR COALESCE(lifecycle_status, '')=''"), eq(Long.class)))
                .thenReturn(1L);
        JdbcSkillGovernanceMigrationContributor contributor = contributor(jdbc);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(3, inspection.totalRows());
        assertEquals(1, inspection.currentRows());
        assertEquals(1, inspection.backfillableRows());
        assertEquals(1, inspection.manualReviewRows());
        assertEquals(List.of("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED"),
                inspection.reasonCodes());
    }

    @Test
    void invalidTypedGovernanceStateMustRequireManualReview() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                contains("information_schema.columns"), eq(Long.class), any(Object[].class)))
                .thenReturn(10L);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM ai_ops_skill"), eq(Long.class)))
                .thenReturn(2L);
        when(jdbc.queryForObject(
                contains("legacy_frozen_classification_required=1"), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.queryForObject(
                contains("UPPER(lifecycle_status) NOT IN"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                contains("OR COALESCE(lifecycle_status, '')=''"), eq(Long.class)))
                .thenReturn(0L);
        JdbcSkillGovernanceMigrationContributor contributor = contributor(jdbc);

        PlatformCapabilityMigrationInspection inspection = contributor.inspect(command());

        assertEquals(1, inspection.currentRows());
        assertEquals(0, inspection.backfillableRows());
        assertEquals(1, inspection.manualReviewRows());
        assertEquals(List.of("SKILL_GOVERNANCE_INVALID_STATE_MANUAL_REVIEW_REQUIRED"),
                inspection.reasonCodes());
    }

    @Test
    void legacyFrozenBackfillMustRemainManualReviewInsteadOfBecomingEnabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("lock_type='LEGACY_UNCLASSIFIED'"))).thenReturn(1);
        when(jdbc.update(contains("mutation_mode=CASE"))).thenReturn(1);
        JdbcSkillGovernanceMigrationContributor contributor = contributor(jdbc);
        PlatformCapabilityMigrationInspection inspection =
                new PlatformCapabilityMigrationInspection(
                        PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE,
                        2, 0, 2, 0, true, true, List.of());

        PlatformCapabilityMigrationBackfillOutcome outcome =
                contributor.backfill(command(), inspection);

        assertEquals(2, outcome.attemptedRows());
        assertEquals(1, outcome.backfilledRows());
        assertEquals(1, outcome.manualReviewRows());
        assertEquals(0, outcome.failedRows());
        assertEquals(List.of("SKILL_LEGACY_FROZEN_BACKFILLED_FAIL_CLOSED"),
                outcome.reasonCodes());
    }

    private JdbcSkillGovernanceMigrationContributor contributor(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new JdbcSkillGovernanceMigrationContributor(provider);
    }

    private PlatformCapabilityMigrationCommand command() {
        return new PlatformCapabilityMigrationCommand(
                "migration-1", "operator", false, 100,
                List.of(PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE));
    }
}
