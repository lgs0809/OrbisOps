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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Read-only real-data scan for the ordered Project -> Legacy -> Source Repository MCP chain. */
@Repository
public class JdbcMcpRuntimeSourceMigrationContributor
        implements PlatformCapabilityMigrationContributor {

    private static final List<String> PROJECT_COLUMNS = List.of(
            "mcp_id", "project_id", "resource_id", "resource_type",
            "transport_type", "transport_config_json", "permission_policy_json",
            "request_timeout", "status", "enabled");
    private static final List<String> LEGACY_COLUMNS = List.of(
            "mcp_id", "transport_type", "transport_config", "request_timeout", "status");

    private final JdbcTemplate jdbc;

    public JdbcMcpRuntimeSourceMigrationContributor(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbc = provider.getIfAvailable();
    }

    @Override
    public PlatformCapabilityMigrationStep step() {
        return PlatformCapabilityMigrationStep.MCP_RUNTIME_SOURCE_CHAIN;
    }

    @Override
    public PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command) {
        if (jdbc == null) return failed("MCP_RUNTIME_STORE_UNAVAILABLE");
        if (!tableColumnsReady("ai_ops_project_mcp", PROJECT_COLUMNS)) {
            return failed("PROJECT_MCP_RUNTIME_SCHEMA_MISSING");
        }
        boolean legacyPresent = tableExists("ai_client_tool_mcp");
        if (legacyPresent && !tableColumnsReady("ai_client_tool_mcp", LEGACY_COLUMNS)) {
            return failed("LEGACY_MCP_RUNTIME_SCHEMA_INCOMPATIBLE");
        }
        long projectTotal = count("SELECT COUNT(*) FROM ai_ops_project_mcp WHERE enabled=1");
        long projectInvalid = count("""
                SELECT COUNT(*) FROM ai_ops_project_mcp
                 WHERE enabled=1 AND (
                       COALESCE(mcp_id,'')=''
                    OR COALESCE(project_id,'')=''
                    OR COALESCE(resource_type,'')=''
                    OR COALESCE(transport_type,'')=''
                    OR request_timeout<=0
                    OR UPPER(COALESCE(status,'')) NOT IN
                       ('PENDING_REVIEW','STALE','ENABLED','DISABLED'))
                """);
        long legacyTotal = legacyPresent
                ? count("SELECT COUNT(*) FROM ai_client_tool_mcp WHERE status=1")
                : 0L;
        long legacyShadowed = legacyPresent
                ? count("""
                        SELECT COUNT(*)
                          FROM ai_client_tool_mcp legacy
                         WHERE legacy.status=1
                           AND EXISTS (
                               SELECT 1 FROM ai_ops_project_mcp project
                                WHERE project.enabled=1
                                  AND project.mcp_id=legacy.mcp_id
                                  AND UPPER(project.status)='ENABLED')
                        """)
                : 0L;
        long legacyUnmapped = Math.max(0L, legacyTotal - legacyShadowed);
        long manual = projectInvalid + legacyUnmapped;
        long total = projectTotal + legacyTotal;
        long current = Math.max(0L, total - manual);
        Set<String> reasons = new LinkedHashSet<>();
        if (projectInvalid > 0) reasons.add("PROJECT_MCP_RUNTIME_DESCRIPTOR_INVALID");
        if (legacyUnmapped > 0) reasons.add("LEGACY_MCP_RUNTIME_MAPPING_REQUIRED");
        if (!legacyPresent) reasons.add("LEGACY_MCP_TABLE_ABSENT_NO_ROWS_TO_MIGRATE");
        return new PlatformCapabilityMigrationInspection(
                step(), total, current, 0, manual,
                true, true, reasons.stream().sorted().toList());
    }

    @Override
    public PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection) {
        return PlatformCapabilityMigrationBackfillOutcome.none(step());
    }

    private boolean tableColumnsReady(String table, List<String> columns) {
        if (!tableExists(table)) return false;
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

    private boolean tableExists(String table) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name=?
                """, Long.class, table);
        return count != null && count == 1L;
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
