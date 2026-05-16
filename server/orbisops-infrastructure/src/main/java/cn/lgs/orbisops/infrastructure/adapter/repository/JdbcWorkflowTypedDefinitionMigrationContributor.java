package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationBackfillOutcome;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationContributor;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import cn.lgs.orbisops.application.migration.WorkflowDefinitionDocumentMigrationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real compatibility scan and bounded CAS backfill for persisted workflow definitions. */
@Repository
public class JdbcWorkflowTypedDefinitionMigrationContributor
        implements PlatformCapabilityMigrationContributor {

    private static final List<String> TABLES = List.of(
            "ai_ops_agent_definition",
            "ai_ops_agent_definition_version");
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "agent_id", "version", "definition_json", "definition_hash");

    private final JdbcTemplate jdbc;
    private final WorkflowDefinitionDocumentMigrationPort documents;

    public JdbcWorkflowTypedDefinitionMigrationContributor(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider,
            WorkflowDefinitionDocumentMigrationPort documents) {
        this.jdbc = provider.getIfAvailable();
        if (documents == null) {
            throw new IllegalArgumentException("WORKFLOW_DEFINITION_DOCUMENT_MIGRATION_PORT_REQUIRED");
        }
        this.documents = documents;
    }

    @Override
    public PlatformCapabilityMigrationStep step() {
        return PlatformCapabilityMigrationStep.WORKFLOW_TYPED_DEFINITION;
    }

    @Override
    public PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command) {
        if (jdbc == null) return failed("WORKFLOW_DEFINITION_STORE_UNAVAILABLE");
        if (!schemaReady()) return failed("WORKFLOW_DEFINITION_SCHEMA_MISSING");
        List<DefinitionRow> rows = rows(0);
        long current = 0L;
        long backfillable = 0L;
        long manual = 0L;
        Set<String> reasons = new LinkedHashSet<>();
        for (DefinitionRow row : rows) {
            WorkflowDefinitionDocumentMigrationPort.MigrationDocument migrated =
                    documents.migrate(row.definitionJson());
            if (migrated.manualReviewRequired()) {
                manual++;
                reasons.add(migrated.reasonCode());
            } else if (!migrated.current()
                    || !migrated.definitionHash().equals(normalizeHash(row.definitionHash()))) {
                backfillable++;
                if (!migrated.reasonCode().isBlank()) reasons.add(migrated.reasonCode());
                if (!migrated.definitionHash().equals(normalizeHash(row.definitionHash()))) {
                    reasons.add("WORKFLOW_DEFINITION_HASH_BACKFILL_REQUIRED");
                }
            } else {
                current++;
            }
        }
        return new PlatformCapabilityMigrationInspection(
                step(), rows.size(), current, backfillable, manual,
                true, true, reasons.stream().sorted().toList());
    }

    @Override
    public PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection) {
        if (jdbc == null || inspection.backfillableRows() == 0) {
            return PlatformCapabilityMigrationBackfillOutcome.none(step());
        }
        int limit = Math.max(1, Math.min(command.batchSize(), 10_000));
        long attempted = 0L;
        long backfilled = 0L;
        long manual = 0L;
        long failed = 0L;
        Set<String> reasons = new LinkedHashSet<>();
        for (DefinitionRow row : rows(limit)) {
            WorkflowDefinitionDocumentMigrationPort.MigrationDocument migrated =
                    documents.migrate(row.definitionJson());
            if (migrated.manualReviewRequired()) {
                manual++;
                reasons.add(migrated.reasonCode());
                continue;
            }
            if (migrated.current()
                    && migrated.definitionHash().equals(normalizeHash(row.definitionHash()))) {
                continue;
            }
            attempted++;
            int updated = update(row, migrated);
            if (updated == 1) {
                backfilled++;
            } else {
                failed++;
                reasons.add("WORKFLOW_DEFINITION_CAS_DRIFT");
            }
        }
        return new PlatformCapabilityMigrationBackfillOutcome(
                step(), attempted, backfilled, manual, failed,
                reasons.stream().sorted().toList());
    }

    private List<DefinitionRow> rows(int limit) {
        String sql = """
                SELECT 'CURRENT' AS source_table, agent_id, version, definition_json, definition_hash
                  FROM ai_ops_agent_definition
                UNION ALL
                SELECT 'VERSION' AS source_table, agent_id, version, definition_json, definition_hash
                  FROM ai_ops_agent_definition_version
                ORDER BY source_table, agent_id, version
                """ + (limit > 0 ? " LIMIT " + limit : "");
        return jdbc.query(sql, (rs, rowNum) -> new DefinitionRow(
                rs.getString("source_table"),
                rs.getString("agent_id"),
                rs.getInt("version"),
                rs.getString("definition_json"),
                rs.getString("definition_hash")));
    }

    private int update(
            DefinitionRow row,
            WorkflowDefinitionDocumentMigrationPort.MigrationDocument migrated) {
        if ("CURRENT".equals(row.sourceTable())) {
            return jdbc.update("""
                    UPDATE ai_ops_agent_definition
                       SET definition_json=?, definition_hash=?, update_time=CURRENT_TIMESTAMP
                     WHERE agent_id=? AND version=?
                       AND definition_json=? AND COALESCE(definition_hash,'')=?
                    """,
                    migrated.migratedJson(), migrated.definitionHash(),
                    row.agentId(), row.version(), row.definitionJson(), text(row.definitionHash()));
        }
        return jdbc.update("""
                UPDATE ai_ops_agent_definition_version
                   SET definition_json=?, definition_hash=?, update_time=CURRENT_TIMESTAMP
                 WHERE agent_id=? AND version=?
                   AND definition_json=? AND COALESCE(definition_hash,'')=?
                """,
                migrated.migratedJson(), migrated.definitionHash(),
                row.agentId(), row.version(), row.definitionJson(), text(row.definitionHash()));
    }

    private boolean schemaReady() {
        for (String table : TABLES) {
            Long tableCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name=?
                    """, Long.class, table);
            if (tableCount == null || tableCount != 1L) return false;
            String placeholders = String.join(",", REQUIRED_COLUMNS.stream().map(ignored -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(table);
            args.addAll(REQUIRED_COLUMNS);
            Long columns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema=DATABASE() AND table_name=? "
                            + "AND column_name IN (" + placeholders + ")",
                    Long.class,
                    args.toArray());
            if (columns == null || columns != REQUIRED_COLUMNS.size()) return false;
        }
        return true;
    }

    private PlatformCapabilityMigrationInspection failed(String reason) {
        return new PlatformCapabilityMigrationInspection(
                step(), 0, 0, 0, 0, false, false, List.of(reason));
    }

    private String normalizeHash(String value) {
        String normalized = text(value).toLowerCase();
        return normalized.matches("[a-f0-9]{64}") ? normalized : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record DefinitionRow(
            String sourceTable,
            String agentId,
            int version,
            String definitionJson,
            String definitionHash) {
    }
}
