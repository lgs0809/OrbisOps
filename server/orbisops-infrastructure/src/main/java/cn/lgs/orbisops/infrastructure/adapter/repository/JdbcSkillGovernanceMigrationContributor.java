package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationBackfillOutcome;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationContributor;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Controlled, repeatable backfill for the orthogonal Skill governance columns. */
@Repository
public class JdbcSkillGovernanceMigrationContributor
        implements PlatformCapabilityMigrationContributor {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "lifecycle_status",
            "mutation_mode",
            "execution_mode",
            "binding_mode",
            "lock_type",
            "lock_reason",
            "lock_actor",
            "lock_approval_id",
            "lock_at",
            "legacy_frozen_classification_required");

    private final JdbcTemplate jdbcTemplate;

    public JdbcSkillGovernanceMigrationContributor(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    @Override
    public PlatformCapabilityMigrationStep step() {
        return PlatformCapabilityMigrationStep.SKILL_GOVERNANCE_STATE;
    }

    @Override
    public PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command) {
        if (jdbcTemplate == null) {
            return new PlatformCapabilityMigrationInspection(
                    step(), 0, 0, 0, 0, true, true,
                    List.of("SKILL_GOVERNANCE_STORE_NOT_CONFIGURED"));
        }
        if (!tableExists()) {
            return new PlatformCapabilityMigrationInspection(
                    step(), 0, 0, 0, 0, false, false,
                    List.of("SKILL_GOVERNANCE_TABLE_MISSING"));
        }
        boolean columnsReady = requiredColumnsReady();
        if (!columnsReady) {
            return new PlatformCapabilityMigrationInspection(
                    step(), 0, 0, 0, 0, false, false,
                    List.of("SKILL_GOVERNANCE_COLUMNS_MISSING"));
        }
        long total = count("SELECT COUNT(*) FROM ai_ops_skill");
        long legacyManual = count("""
                SELECT COUNT(*) FROM ai_ops_skill
                WHERE legacy_frozen_classification_required=1
                """);
        long invalidManual = count("""
                SELECT COUNT(*) FROM ai_ops_skill
                WHERE legacy_frozen_classification_required=0
                  AND UPPER(COALESCE(status, ''))<>'FROZEN'
                  AND UPPER(COALESCE(update_mode, ''))<>'FROZEN'
                  AND COALESCE(lifecycle_status, '')<>''
                  AND COALESCE(mutation_mode, '')<>''
                  AND COALESCE(execution_mode, '')<>''
                  AND COALESCE(binding_mode, '')<>''
                  AND (
                    UPPER(lifecycle_status) NOT IN ('DRAFT','ACTIVE','PAUSED','DEPRECATED','RETIRED')
                    OR UPPER(mutation_mode) NOT IN ('AUTO','MANUAL_ONLY','LOCKED','SEALED')
                    OR UPPER(execution_mode) NOT IN ('ENABLED','SHADOW_ONLY','QUARANTINED','DISABLED')
                    OR UPPER(binding_mode) NOT IN ('FLOATING','PINNED')
                    OR (COALESCE(lock_type, '')<>'' AND UPPER(lock_type) NOT IN
                      ('NONE','MANUAL_LOCK','STABILITY_LOCK','INCIDENT_QUARANTINE',
                       'COMPLIANCE_SEAL','LEGACY_UNCLASSIFIED'))
                  )
                """);
        long manual = legacyManual + invalidManual;
        long backfillable = count("""
                SELECT COUNT(*) FROM ai_ops_skill
                WHERE legacy_frozen_classification_required=0
                  AND (
                    UPPER(COALESCE(status, ''))='FROZEN'
                    OR UPPER(COALESCE(update_mode, ''))='FROZEN'
                    OR COALESCE(lifecycle_status, '')=''
                    OR COALESCE(mutation_mode, '')=''
                    OR COALESCE(execution_mode, '')=''
                    OR COALESCE(binding_mode, '')=''
                  )
                """);
        long current = total - manual - backfillable;
        List<String> reasons = legacyManual > 0 && invalidManual > 0
                ? List.of(
                        "SKILL_GOVERNANCE_INVALID_STATE_MANUAL_REVIEW_REQUIRED",
                        "SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED")
                : legacyManual > 0
                        ? List.of("SKILL_LEGACY_FROZEN_CLASSIFICATION_REQUIRED")
                        : invalidManual > 0
                                ? List.of("SKILL_GOVERNANCE_INVALID_STATE_MANUAL_REVIEW_REQUIRED")
                                : List.of();
        return new PlatformCapabilityMigrationInspection(
                step(), total, current, backfillable, manual, true, true, reasons);
    }

    @Override
    public PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection) {
        if (jdbcTemplate == null || inspection.backfillableRows() == 0) {
            return PlatformCapabilityMigrationBackfillOutcome.none(step());
        }
        int limit = Math.min(command.batchSize(), 10_000);
        try {
            int legacyFrozen = jdbcTemplate.update("""
                    UPDATE ai_ops_skill
                    SET lifecycle_status='ACTIVE',
                        mutation_mode='LOCKED',
                        execution_mode='QUARANTINED',
                        binding_mode=COALESCE(NULLIF(binding_mode, ''), 'FLOATING'),
                        lock_type='LEGACY_UNCLASSIFIED',
                        lock_reason=CASE
                          WHEN COALESCE(frozen_reason, '')=''
                            THEN 'legacy FROZEN classification required'
                          ELSE frozen_reason
                        END,
                        lock_actor=CASE
                          WHEN COALESCE(frozen_by, '')=''
                            THEN 'SYSTEM_LEGACY_MIGRATION'
                          ELSE frozen_by
                        END,
                        lock_approval_id='',
                        lock_at=COALESCE(frozen_at, create_time),
                        legacy_frozen_classification_required=1
                    WHERE legacy_frozen_classification_required=0
                      AND (UPPER(COALESCE(status, ''))='FROZEN'
                        OR UPPER(COALESCE(update_mode, ''))='FROZEN')
                    LIMIT """ + limit);
            int ordinary = jdbcTemplate.update("""
                    UPDATE ai_ops_skill
                    SET lifecycle_status=CASE UPPER(COALESCE(status, 'ENABLED'))
                          WHEN 'DRAFT' THEN 'DRAFT'
                          WHEN 'PAUSED' THEN 'PAUSED'
                          WHEN 'DEPRECATED' THEN 'DEPRECATED'
                          WHEN 'RETIRED' THEN 'RETIRED'
                          WHEN 'REJECTED' THEN 'RETIRED'
                          ELSE 'ACTIVE'
                        END,
                        mutation_mode=CASE UPPER(COALESCE(update_mode, 'MANUAL_ONLY'))
                          WHEN 'AUTO' THEN 'AUTO'
                          WHEN 'LOCKED' THEN 'LOCKED'
                          WHEN 'SEALED' THEN 'SEALED'
                          ELSE 'MANUAL_ONLY'
                        END,
                        execution_mode=CASE UPPER(COALESCE(status, 'ENABLED'))
                          WHEN 'DRAFT' THEN 'DISABLED'
                          WHEN 'PAUSED' THEN 'DISABLED'
                          WHEN 'DEPRECATED' THEN 'DISABLED'
                          WHEN 'RETIRED' THEN 'DISABLED'
                          WHEN 'REJECTED' THEN 'DISABLED'
                          WHEN 'DISABLED' THEN 'DISABLED'
                          ELSE 'ENABLED'
                        END,
                        binding_mode=COALESCE(NULLIF(binding_mode, ''), 'FLOATING')
                    WHERE legacy_frozen_classification_required=0
                      AND UPPER(COALESCE(status, ''))<>'FROZEN'
                      AND UPPER(COALESCE(update_mode, ''))<>'FROZEN'
                      AND (COALESCE(lifecycle_status, '')=''
                        OR COALESCE(mutation_mode, '')=''
                        OR COALESCE(execution_mode, '')=''
                        OR COALESCE(binding_mode, '')='')
                    LIMIT """ + Math.max(0, limit - legacyFrozen));
            long changed = (long) legacyFrozen + ordinary;
            return new PlatformCapabilityMigrationBackfillOutcome(
                    step(), changed, ordinary, legacyFrozen, 0,
                    legacyFrozen > 0
                            ? List.of("SKILL_LEGACY_FROZEN_BACKFILLED_FAIL_CLOSED")
                            : List.of());
        } catch (DataAccessException error) {
            return new PlatformCapabilityMigrationBackfillOutcome(
                    step(), inspection.backfillableRows(), 0, 0,
                    inspection.backfillableRows(),
                    List.of("SKILL_GOVERNANCE_BACKFILL_FAILED"));
        }
    }

    private boolean tableExists() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name='ai_ops_skill'
                """, Long.class);
        return count != null && count > 0;
    }

    private boolean requiredColumnsReady() {
        String placeholders = String.join(",", REQUIRED_COLUMNS.stream().map(ignored -> "?").toList());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name='ai_ops_skill' "
                        + "AND column_name IN (" + placeholders + ")",
                Long.class,
                REQUIRED_COLUMNS.toArray());
        return count != null && count == REQUIRED_COLUMNS.size();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
