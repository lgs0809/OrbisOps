package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationBackfillOutcome;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationContributor;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** Real persisted-run scan for frozen bound workflow and durable checkpoint identity. */
@Repository
public class JdbcBoundWorkflowSnapshotMigrationContributor
        implements PlatformCapabilityMigrationContributor {

    private static final List<String> RUN_COLUMNS = List.of(
            "run_id", "project_id", "status", "agent_definition_hash",
            "execution_harness", "run_manifest_json", "run_manifest_hash");
    private static final List<String> CHECKPOINT_COLUMNS = List.of(
            "run_id", "project_id", "attempt_id", "checkpoint_seq",
            "checkpoint_type", "checkpoint_json", "checkpoint_hash", "delivery_key");

    private final JdbcTemplate jdbc;

    public JdbcBoundWorkflowSnapshotMigrationContributor(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbc = provider.getIfAvailable();
    }

    @Override
    public PlatformCapabilityMigrationStep step() {
        return PlatformCapabilityMigrationStep.BOUND_WORKFLOW_SNAPSHOT;
    }

    @Override
    public PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command) {
        if (jdbc == null) return failed("BOUND_WORKFLOW_STORE_UNAVAILABLE");
        if (!tableColumnsReady("ai_ops_agent_run", RUN_COLUMNS)
                || !tableColumnsReady("ai_ops_agent_run_checkpoint", CHECKPOINT_COLUMNS)) {
            return failed("BOUND_WORKFLOW_DURABILITY_SCHEMA_MISSING");
        }
        long total = count("""
                SELECT COUNT(*) FROM ai_ops_agent_run
                 WHERE status IN ('PENDING','RUNNING','RECOVERABLE')
                """);
        long current = count("""
                SELECT COUNT(*)
                  FROM ai_ops_agent_run run
                 WHERE run.status IN ('PENDING','RUNNING','RECOVERABLE')
                   AND JSON_VALID(run.run_manifest_json)=1
                   AND run.run_manifest_hash REGEXP '^[0-9a-fA-F]{64}$'
                   AND run.agent_definition_hash REGEXP '^[0-9a-fA-F]{64}$'
                   AND EXISTS (
                       SELECT 1
                         FROM ai_ops_agent_run_checkpoint checkpoint_row
                        WHERE checkpoint_row.run_id=run.run_id
                          AND checkpoint_row.project_id=run.project_id
                          AND checkpoint_row.checkpoint_type LIKE 'TYPED_WORKFLOW_%'
                          AND JSON_VALID(checkpoint_row.checkpoint_json)=1
                          AND JSON_EXTRACT(checkpoint_row.checkpoint_json,'$.schemaVersion')=1
                          AND JSON_UNQUOTE(JSON_EXTRACT(
                                checkpoint_row.checkpoint_json,'$.state.runId'))=run.run_id
                          AND JSON_UNQUOTE(JSON_EXTRACT(
                                checkpoint_row.checkpoint_json,'$.state.projectId'))=run.project_id
                          AND JSON_UNQUOTE(JSON_EXTRACT(
                                checkpoint_row.checkpoint_json,'$.state.definitionHash'))
                                =run.agent_definition_hash
                          AND checkpoint_row.checkpoint_hash REGEXP '^[0-9a-fA-F]{64}$'
                   )
                """);
        long manual = Math.max(0L, total - current);
        List<String> reasons = manual > 0
                ? List.of("BOUND_WORKFLOW_SNAPSHOT_RECONSTRUCTION_UNSAFE")
                : List.of();
        return new PlatformCapabilityMigrationInspection(
                step(), total, current, 0, manual, true, true, reasons);
    }

    @Override
    public PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection) {
        return PlatformCapabilityMigrationBackfillOutcome.none(step());
    }

    private boolean tableColumnsReady(String table, List<String> columns) {
        Long tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name=?
                """, Long.class, table);
        if (tableCount == null || tableCount != 1L) return false;
        String placeholders = String.join(",", columns.stream().map(ignored -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(table);
        args.addAll(columns);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? "
                        + "AND column_name IN (" + placeholders + ")",
                Long.class,
                args.toArray());
        return count != null && count == columns.size();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private PlatformCapabilityMigrationInspection failed(String reason) {
        return new PlatformCapabilityMigrationInspection(
                step(), 0, 0, 0, 0, false, false, List.of(reason));
    }
}
